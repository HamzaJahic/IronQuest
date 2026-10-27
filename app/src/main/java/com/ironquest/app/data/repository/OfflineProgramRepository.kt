package com.ironquest.app.data.repository

import androidx.room.withTransaction
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.entity.ExerciseEntity
import com.ironquest.app.data.local.entity.ProgramDayEntity
import com.ironquest.app.data.local.entity.ProgramEntity
import com.ironquest.app.data.local.entity.ProgramExerciseEntity
import com.ironquest.app.data.local.entity.ProgramExerciseRow
import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.ExerciseImport
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.ProgramException
import com.ironquest.app.domain.repository.ProgramProblem
import com.ironquest.app.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineProgramRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val clock: Clock,
    private val settings: AppSettingsRepository,
) : ProgramRepository {

    private val dao = db.programDao()

    private val imported: Flow<List<Program>> = dao.observeAll().map(::toPrograms).distinctUntilChanged()

    private val builtInHidden = settings.observeBuiltInProgramHidden()

    override fun observeBuiltInHidden(): Flow<Boolean> = builtInHidden

    override fun observePrograms(): Flow<List<Program>> = combine(imported, builtInHidden) { list, hidden ->
        (if (hidden) emptyList() else listOf(builtIn(active = list.none { it.isActive }))) + list
    }

    override fun observeActiveProgram(): Flow<Program?> = combine(imported, builtInHidden) { list, hidden ->
        list.firstOrNull { it.isActive } ?: if (hidden) null else builtIn(active = true)
    }.distinctUntilChanged()

    override suspend fun findTemplate(templateId: String): WorkoutTemplate? {
        val dayId = Program.customDayId(templateId)
            ?: return ProgramCatalog.templates.firstOrNull { it.id == templateId }
        return toPrograms(dao.dayRows(dayId)).firstOrNull()?.templates?.firstOrNull()
    }

    override suspend fun importProgram(name: String, days: List<DayImport>): Long = db.withTransaction {
        require(days.isNotEmpty()) { "A program needs at least one day" }
        val id = dao.insertProgram(ProgramEntity(name = name.trim(), isActive = false, createdAtMillis = clock.millis()))
        insertDays(id, days, startPosition = 0)
        dao.clearActive()
        dao.activate(id)
        id
    }

    override suspend fun addDays(programId: Long, days: List<DayImport>) = db.withTransaction {
        if (dao.getProgram(programId) == null) throw ProgramException(ProgramProblem.ProgramGone)
        insertDays(programId, days, startPosition = dao.maxDayPosition(programId) + 1)
    }

    override suspend fun addExercises(templateId: String, exercises: List<ExerciseImport>) = db.withTransaction {
        val dayId = Program.customDayId(templateId) ?: throw ProgramException(ProgramProblem.BuiltInReadOnly)
        if (dao.dayRows(dayId).isEmpty()) throw ProgramException(ProgramProblem.DayGone)
        val existing = dao.exerciseIds(dayId).toSet()
        val clashes = exercises.filter { it.definition.id in existing }
        if (clashes.isNotEmpty()) throw ProgramException(ProgramProblem.AlreadyInDay(clashes.map { it.definition }))
        // Superset labels are scoped to this import: never merge with the day's existing groups.
        val renamed = uniqueSupersetKeys(exercises, taken = dao.supersetGroups(dayId).toSet())
        insertExercises(dayId, renamed, startPosition = dao.maxExercisePosition(dayId) + 1)
    }

    override suspend fun setActiveProgram(id: Long?) {
        db.withTransaction {
            if (id == null && settings.observeBuiltInProgramHidden().first()) throw ProgramException(ProgramProblem.RestoreBuiltInFirst)
            dao.clearActive()
            if (id != null && dao.activate(id) != 1) throw ProgramException(ProgramProblem.ProgramGone)
        }
        // The new program ignores the choice anyway (its day ids differ); clearing keeps it from coming back on a switch back.
        settings.setNextWorkoutChoice(null)
    }

    override suspend fun deleteProgram(id: Long?) = db.withTransaction {
        val templateIds = if (id == null) ProgramCatalog.templates.map { it.id } else dao.dayIds(id).map(Program::customTemplateId)
        if (templateIds.isNotEmpty()) {
            if (dao.countInProgress(templateIds) != 0) throw ProgramException(ProgramProblem.WorkoutInProgress)
        }
        // The built-in program is "active" while no imported one is.
        val wasActive = if (id == null) dao.activeCount() == 0 else dao.getProgram(id)?.isActive == true
        if (id == null) settings.setBuiltInProgramHidden(true) else dao.deleteProgram(id)
        // With the built-in program gone there is nothing to fall back to: hand over to the newest one left.
        if (wasActive && settings.observeBuiltInProgramHidden().first()) dao.latestProgramId()?.let { dao.activate(it) }
    }

    override suspend fun restoreBuiltIn() {
        settings.setBuiltInProgramHidden(false)
    }

    private suspend fun insertDays(programId: Long, days: List<DayImport>, startPosition: Int) {
        days.forEachIndexed { index, day ->
            val dayId = dao.insertDay(ProgramDayEntity(programId = programId, name = day.name.trim(), position = startPosition + index))
            insertExercises(dayId, day.exercises, startPosition = 0)
        }
    }

    private suspend fun insertExercises(dayId: Long, exercises: List<ExerciseImport>, startPosition: Int) {
        exercises.forEach { imported ->
            val definition = imported.definition
            dao.insertExerciseIfMissing(ExerciseEntity(definition.id, definition.name, definition.stat, definition.trackingType, definition.incrementKg))
        }
        dao.insertExercises(
            exercises.mapIndexed { index, imported ->
                ProgramExerciseEntity(
                    dayId = dayId,
                    exerciseId = imported.definition.id,
                    targetSets = imported.targetSets,
                    repMin = imported.repRange.min,
                    repMax = imported.repRange.max,
                    position = startPosition + index,
                    supersetGroup = imported.supersetGroup,
                )
            },
        )
    }

    private fun builtIn(active: Boolean) = Program(
        id = null,
        name = "", // not UI text: the built-in name is a resource, chosen by the UI when id == null (ui/catalog/programNameText)
        isActive = active,
        templates = ProgramCatalog.templates,
    )

    companion object {
        /** Renames imported superset keys that clash with [taken] ("a" → "a~2", "a~3", …). */
        internal fun uniqueSupersetKeys(exercises: List<ExerciseImport>, taken: Set<String>): List<ExerciseImport> {
            val used = taken.toMutableSet()
            val renames = mutableMapOf<String, String>()
            exercises.mapNotNull { it.supersetGroup }.distinct().forEach { key ->
                var candidate = key
                var suffix = 2
                while (candidate in used) candidate = "$key~${suffix++}" // not UI text: stored superset key
                used += candidate
                renames[key] = candidate
            }
            return exercises.map { imported -> imported.supersetGroup?.let { imported.copy(supersetGroup = renames.getValue(it)) } ?: imported }
        }

        /**
         * Groups the flat join rows into programs. Days without exercises are dropped (a
         * template needs a main lift); a program left with no days is dropped entirely, so it
         * can never become the empty active program.
         */
        internal fun toPrograms(rows: List<ProgramExerciseRow>): List<Program> = rows.groupBy { it.programId }.mapNotNull { (programId, programRows) ->
            val head = programRows.first()
            val templates = programRows
                .filter { it.dayId != null }
                .groupBy { checkNotNull(it.dayId) { "filtered above: only rows with a day reach here" } }
                .mapNotNull { (dayId, dayRows) ->
                    val exercises = dayRows.mapNotNull { it.toTemplateExercise() }
                    if (exercises.isEmpty()) {
                        null
                    } else {
                        WorkoutTemplate(
                            id = Program.customTemplateId(dayId),
                            name = dayRows.first().dayName.orEmpty(),
                            exercises = exercises,
                        )
                    }
                }
            if (templates.isEmpty()) null else Program(programId, head.programName, head.programActive, templates)
        }

        private fun ProgramExerciseRow.toTemplateExercise(): TemplateExercise? {
            val definition = ExerciseDefinition(
                id = exerciseId ?: return null,
                name = exerciseName ?: return null,
                stat = stat ?: return null,
                trackingType = trackingType ?: return null,
                incrementKg = incrementKg ?: return null,
            )
            val min = repMin ?: return null
            val max = repMax ?: return null
            if (min !in 1..max) return null
            return TemplateExercise(definition, targetSets ?: return null, RepRange(min, max), supersetGroup)
        }
    }
}
