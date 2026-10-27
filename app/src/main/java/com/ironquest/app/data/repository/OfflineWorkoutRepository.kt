package com.ironquest.app.data.repository

import androidx.room.withTransaction
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.dao.PlayerProfileDao
import com.ironquest.app.data.local.entity.PlayerProfileEntity
import com.ironquest.app.data.local.entity.SetDetailRow
import com.ironquest.app.data.local.entity.SetEntity
import com.ironquest.app.data.local.entity.WorkoutEntity
import com.ironquest.app.data.local.entity.WorkoutWithSets
import com.ironquest.app.domain.goals.GoalAward
import com.ironquest.app.domain.goals.GoalRules
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.CompletedWorkoutLog
import com.ironquest.app.domain.model.ExerciseLog
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.InProgressSession
import com.ironquest.app.domain.model.LastCompletion
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.model.LogSetRequest
import com.ironquest.app.domain.model.LogSetResult
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.PersistedSet
import com.ironquest.app.domain.model.PlayerProfile
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.SaveWorkoutResult
import com.ironquest.app.domain.model.StatProgress
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.WorkoutCompleteResult
import com.ironquest.app.domain.model.WorkoutLogDraft
import com.ironquest.app.domain.progression.LevelCurve
import com.ironquest.app.domain.progression.XpRules
import com.ironquest.app.domain.progression.XpSnapshot
import com.ironquest.app.domain.progression.detectLevelUp
import com.ironquest.app.domain.progression.gateLevelUp
import com.ironquest.app.domain.progression.raisedHighestLevel
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineWorkoutRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val clock: Clock,
    private val programs: ProgramRepository,
) : WorkoutRepository {

    private val workoutDao = db.workoutDao()
    private val setDao = db.setDao()
    private val profileDao = db.playerProfileDao()

    override fun observeProfile(): Flow<PlayerProfile> = profileDao.observe().map { (it ?: PlayerProfileEntity()).toDomain() }

    override fun observeInProgressTemplateId(): Flow<String?> = workoutDao.observeAnyInProgress().map { it?.templateId }.distinctUntilChanged()

    override fun observeLastCompletion(): Flow<LastCompletion?> = workoutDao.observeLastCompletion().map { row -> row?.let { LastCompletion(it.templateId, it.completedAtMillis) } }.distinctUntilChanged()

    override fun observeGhost(templateId: String): Flow<GhostSession?> = workoutDao.observeLastCompletedWithSets(templateId).map { it?.toGhost() }

    override suspend fun findInProgress(templateId: String): InProgressSession? = workoutDao.findInProgressWithSets(templateId)?.let { row ->
        InProgressSession(
            workoutId = row.workout.id,
            templateId = row.workout.templateId,
            setsByExercise = row.sets
                .groupBy { it.exerciseId }
                .mapValues { (_, sets) -> sets.sortedBy { it.setIndex }.map { PersistedSet(it.id, it.toLoggedSet(), it.xpAwarded) } },
        )
    }

    override suspend fun logSet(request: LogSetRequest): LogSetResult = db.withTransaction {
        val now = clock.millis()
        val workoutId = request.workoutId ?: workoutDao.insert(
            WorkoutEntity(
                templateId = request.templateId,
                dateEpochDay = LocalDate.now(clock).toEpochDay(),
                startedAtMillis = now,
            ),
        )
        val definition = request.exercise.exercise
        val xp = XpRules.forSet(request.set, request.ghost, definition.trackingType)

        val setId = setDao.insert(
            SetEntity(
                workoutId = workoutId,
                exerciseId = definition.id,
                setIndex = request.set.setIndex,
                reps = request.set.reps,
                weightKg = request.set.weightKg,
                durationMinutes = request.set.durationMinutes,
                targetRepMin = request.exercise.repRange.min,
                targetRepMax = request.exercise.repRange.max,
                xpAwarded = xp.total,
                loggedAtMillis = now,
            ),
        )

        val before = requireProfile()
        val gained = xp.total
        profileDao.addXp(
            totalXp = gained,
            constitutionXp = if (definition.stat == StatType.CONSTITUTION) gained else 0,
            strengthXp = if (definition.stat == StatType.STRENGTH) gained else 0,
            staminaXp = if (definition.stat == StatType.STAMINA) gained else 0,
        )
        val after = requireProfile()

        LogSetResult(
            setId = setId,
            workoutId = workoutId,
            xp = xp,
            levelUp = profileDao.reportLevelUp(before, after),
        )
    }

    override suspend fun updateSet(setId: Long, set: LoggedSet) {
        setDao.updatePerformance(setId, set.reps, set.weightKg, set.durationMinutes)
    }

    override suspend fun deleteSet(setId: Long): Boolean = db.withTransaction {
        val set = setDao.getById(setId) ?: return@withTransaction false
        val workout = workoutDao.getById(set.workoutId) ?: return@withTransaction false
        // Finished workouts are corrected in the Log editor, which re-prices the whole session.
        if (workout.completedAtMillis != null) return@withTransaction false
        val stat = setDao.statXpForSet(setId)?.stat
        fun statXp(statType: StatType) = if (stat == statType) -set.xpAwarded else 0
        profileDao.adjustXp(
            totalXp = -set.xpAwarded,
            constitutionXp = statXp(StatType.CONSTITUTION),
            strengthXp = statXp(StatType.STRENGTH),
            staminaXp = statXp(StatType.STAMINA),
        )
        setDao.deleteById(setId)
        // Sessions exist only once a set is logged; an emptied one goes too.
        val emptied = setDao.countForWorkout(workout.id) == 0
        if (emptied) workoutDao.deleteById(workout.id)
        emptied
    }

    override suspend fun completeWorkout(workoutId: Long, goalTypes: Set<GoalType>): WorkoutCompleteResult = db.withTransaction {
        val workout = workoutDao.getById(workoutId) ?: error("Workout $workoutId not found")
        // Idempotent: a double tap on "Finish" must not pay the bonuses twice.
        if (workout.completedAtMillis != null) {
            return@withTransaction WorkoutCompleteResult(
                bonusXp = 0,
                totalXpEarned = workout.xpEarned,
                levelUp = null,
                durationMillis = workout.completedAtMillis - workout.startedAtMillis,
            )
        }
        val sets = setDao.getForWorkout(workoutId)
        check(sets.isNotEmpty()) { "Cannot complete a workout with no sets logged" }

        // Goals: this session's final sets versus the previous completed session of the template
        // (this one isn't completed yet, so it can't be its own "last time").
        val template = programs.findTemplate(workout.templateId)
        val previous = workoutDao.getLastCompletedWithSets(workout.templateId)
        val award = template?.let { found ->
            GoalRules.completionAward(found, previous?.sets.orEmpty().byExercise(), sets.byExercise(), goalTypes)
        } ?: GoalAward.None

        val bonus = XpRules.QUEST_COMPLETE_BONUS
        val total = sets.sumOf { it.xpAwarded } + award.goalXp + bonus

        val before = requireProfile()
        // The completion bonus is character XP only; goal bonuses also feed the exercise's stat.
        profileDao.addXp(
            totalXp = bonus + award.goalXp,
            constitutionXp = award.statXp(StatType.CONSTITUTION),
            strengthXp = award.statXp(StatType.STRENGTH),
            staminaXp = award.statXp(StatType.STAMINA),
        )
        profileDao.incrementQuestsCompleted()
        val completedAt = clock.millis()
        workoutDao.update(workout.copy(completedAtMillis = completedAt, xpEarned = total))
        val after = requireProfile()

        WorkoutCompleteResult(
            bonusXp = bonus,
            totalXpEarned = total,
            levelUp = profileDao.reportLevelUp(before, after),
            goals = award.goals,
            durationMillis = completedAt - workout.startedAtMillis,
        )
    }

    override fun observeCompletedDays(month: YearMonth): Flow<Set<LocalDate>> = workoutDao.observeCompletedDays(
        fromEpochDay = month.atDay(1).toEpochDay(),
        toEpochDay = month.atEndOfMonth().toEpochDay(),
    ).map { days -> days.mapTo(mutableSetOf()) { LocalDate.ofEpochDay(it) } }

    override fun observeCompletedCount(from: LocalDate, to: LocalDate): Flow<Int> = workoutDao.observeCompletedCount(from.toEpochDay(), to.toEpochDay())

    override fun observeCompletedDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>> = workoutDao.observeCompletedDates(from.toEpochDay(), to.toEpochDay()).map { days -> days.map(LocalDate::ofEpochDay) }

    override fun observeDayLog(date: LocalDate): Flow<List<CompletedWorkoutLog>> = setDao.observeCompletedSetDetailsForDay(date.toEpochDay()).map { toDayLog(it) }

    override suspend fun findWorkoutLog(workoutId: Long): CompletedWorkoutLog? = toDayLog(setDao.getCompletedSetDetails(workoutId)).firstOrNull()

    override suspend fun ghostFor(templateId: String, date: LocalDate, excludeWorkoutId: Long?): GhostSession? = workoutDao.getLastCompletedBefore(templateId, momentFor(date), excludeWorkoutId ?: 0)?.toGhost()

    override suspend fun saveWorkoutLog(draft: WorkoutLogDraft, goalTypes: Set<GoalType>): SaveWorkoutResult = db.withTransaction {
        require(!draft.date.isAfter(LocalDate.now(clock))) { "Cannot log a workout for a future date" }
        val exercises = draft.exercises
            .map { exercise -> exercise.copy(sets = exercise.sets.mapIndexed { index, set -> set.copy(setIndex = index) }) }
            .filter { it.sets.isNotEmpty() }
        require(exercises.isNotEmpty()) { "Log at least one set" }
        val existing = draft.workoutId?.let { workoutDao.getById(it) ?: error("That workout no longer exists") }
        check(existing == null || existing.completedAtMillis != null) { "Finish that workout from Today" }

        // A past entry is stamped at noon of its day (now, for today); a rewrite keeps its own times.
        val startedAt = existing?.startedAtMillis ?: momentFor(draft.date)
        val completedAt = existing?.completedAtMillis ?: startedAt
        val previous = workoutDao.getLastCompletedBefore(draft.templateId, startedAt, existing?.id ?: 0)
        val lastSets = previous?.sets.orEmpty().byExercise()

        val priced = exercises.map { exercise ->
            exercise to exercise.sets.map { set ->
                XpRules.forSet(set, lastSets[exercise.exercise.id]?.firstOrNull { it.setIndex == set.setIndex }, exercise.exercise.trackingType).total
            }
        }
        val newSetXp = priced.sumOf { (_, setXps) -> setXps.sum() }
        val newStatXp = priced.groupBy({ (exercise, _) -> exercise.exercise.stat }, { (_, setXps) -> setXps.sum() }).mapValues { it.value.sum() }

        val oldStatRows = existing?.let { setDao.statXpForWorkout(it.id) }.orEmpty()
        val oldSetXp = oldStatRows.sumOf { it.xpAwarded }
        val oldStatXp = oldStatRows.groupBy({ it.stat }, { it.xpAwarded }).mapValues { it.value.sum() }

        val bonus = XpRules.QUEST_COMPLETE_BONUS
        // A new session earns goal bonuses like a live one; a rewrite keeps whatever it was paid.
        val award = if (existing == null) {
            programs.findTemplate(draft.templateId)?.let { template ->
                GoalRules.completionAward(template, lastSets, exercises.associate { it.exercise.id to it.sets }, goalTypes)
            } ?: GoalAward.None
        } else {
            GoalAward.None
        }
        val goalXp = if (existing == null) award.goalXp else (existing.xpEarned - oldSetXp - bonus).coerceAtLeast(0)
        val total = newSetXp + goalXp + bonus

        val before = requireProfile()
        val workoutId = if (existing == null) {
            workoutDao.insert(
                WorkoutEntity(
                    templateId = draft.templateId,
                    dateEpochDay = draft.date.toEpochDay(),
                    startedAtMillis = startedAt,
                    completedAtMillis = completedAt,
                    xpEarned = total,
                ),
            )
        } else {
            setDao.deleteForWorkout(existing.id)
            workoutDao.update(existing.copy(xpEarned = total))
            existing.id
        }
        priced.forEach { (exercise, setXps) ->
            exercise.sets.forEachIndexed { index, set ->
                setDao.insert(
                    SetEntity(
                        workoutId = workoutId,
                        exerciseId = exercise.exercise.id,
                        setIndex = index,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        durationMinutes = set.durationMinutes,
                        targetRepMin = exercise.repRange.min,
                        targetRepMax = exercise.repRange.max,
                        xpAwarded = setXps[index],
                        loggedAtMillis = completedAt,
                    ),
                )
            }
        }

        fun stat(statType: StatType) = (newStatXp[statType] ?: 0) + award.statXp(statType) - (oldStatXp[statType] ?: 0)
        profileDao.adjustXp(
            totalXp = if (existing == null) total else newSetXp - oldSetXp,
            constitutionXp = stat(StatType.CONSTITUTION),
            strengthXp = stat(StatType.STRENGTH),
            staminaXp = stat(StatType.STAMINA),
        )
        if (existing == null) profileDao.incrementQuestsCompleted()
        val after = requireProfile()
        SaveWorkoutResult(
            workoutId = workoutId,
            xpDelta = (after.totalXp - before.totalXp).toInt(),
            levelUp = profileDao.reportLevelUp(before, after),
        )
    }

    override suspend fun deleteWorkout(workoutId: Long) = db.withTransaction {
        val workout = workoutDao.getById(workoutId) ?: return@withTransaction
        val rows = setDao.statXpForWorkout(workoutId)
        val finished = workout.completedAtMillis != null
        fun stat(statType: StatType) = -rows.filter { it.stat == statType }.sumOf { it.xpAwarded }
        // A finished workout paid sets + goals + completion bonus (xpEarned); an unfinished one only its sets.
        profileDao.adjustXp(
            totalXp = -(if (finished) workout.xpEarned else rows.sumOf { it.xpAwarded }),
            constitutionXp = stat(StatType.CONSTITUTION),
            strengthXp = stat(StatType.STRENGTH),
            staminaXp = stat(StatType.STAMINA),
        )
        if (finished) profileDao.adjustQuestsCompleted(-1)
        workoutDao.deleteById(workoutId)
    }

    /** Where a session on [date] sits in time: noon for a past day, now for today. */
    private fun momentFor(date: LocalDate): Long = if (!date.isBefore(LocalDate.now(clock))) {
        clock.millis()
    } else {
        date.atTime(LocalTime.NOON).atZone(clock.zone).toInstant().toEpochMilli()
    }

    private suspend fun requireProfile(): PlayerProfileEntity = profileDao.get() ?: error("player_profile row missing — SeedCallback did not run")

    private suspend fun toDayLog(rows: List<SetDetailRow>): List<CompletedWorkoutLog> {
        // Imported days may have been deleted since; their history stays, under a generic name.
        val templates = rows.map { it.templateId }.distinct().associateWith { programs.findTemplate(it) }
        return rows.groupBy { it.workoutId }.map { (workoutId, workoutRows) ->
            val first = workoutRows.first()
            val template = templates[first.templateId]
            val templateOrder = template?.exercises?.map { it.exercise.id }.orEmpty()
            CompletedWorkoutLog(
                workoutId = workoutId,
                templateId = first.templateId,
                date = LocalDate.ofEpochDay(first.dateEpochDay),
                templateName = template?.name ?: first.templateId.takeIf { Program.customDayId(it) == null },
                completedAtMillis = first.completedAtMillis,
                xpEarned = first.workoutXp,
                exercises = workoutRows
                    .groupBy { it.exerciseId }
                    .map { (exerciseId, sets) ->
                        val head = sets.first()
                        ExerciseLog(
                            exerciseId = exerciseId,
                            name = head.exerciseName,
                            stat = head.stat,
                            trackingType = head.trackingType,
                            sets = sets.sortedBy { it.setIndex }.map { set ->
                                LoggedSet(set.setIndex, set.reps, set.weightKg, set.durationMinutes)
                            },
                            repRange = RepRange(head.targetRepMin.coerceAtLeast(1), maxOf(head.targetRepMin, head.targetRepMax, 1)),
                        )
                    }
                    .sortedBy { log -> templateOrder.indexOf(log.exerciseId).let { if (it == -1) Int.MAX_VALUE else it } },
            )
        }
    }
}

private fun WorkoutWithSets.toGhost() = GhostSession(
    workoutId = workout.id,
    date = LocalDate.ofEpochDay(workout.dateEpochDay),
    setsByExercise = sets.byExercise(),
)

private fun SetEntity.toLoggedSet() = LoggedSet(setIndex, reps, weightKg, durationMinutes)

private fun List<SetEntity>.byExercise(): Map<String, List<LoggedSet>> = groupBy { it.exerciseId }.mapValues { (_, sets) -> sets.sortedBy { it.setIndex }.map { it.toLoggedSet() } }

/**
 * The level-up to report for an XP write, given profile rows read before and after it inside
 * the same transaction: character levels at or below the highest ever reached are not reported
 * again (character XP can drop when meals are un-logged). Raises the mark in that transaction.
 */
internal suspend fun PlayerProfileDao.reportLevelUp(before: PlayerProfileEntity, after: PlayerProfileEntity): LevelUpEvent? {
    val event = gateLevelUp(detectLevelUp(before.snapshot(), after.snapshot()), before.highestLevelReached)
    val highest = raisedHighestLevel(before.highestLevelReached, after.totalXp)
    if (highest > before.highestLevelReached) raiseHighestLevel(highest)
    return event
}

internal fun PlayerProfileEntity.snapshot() = XpSnapshot(
    totalXp = totalXp,
    statXp = mapOf(
        StatType.CONSTITUTION to constitutionXp,
        StatType.STRENGTH to strengthXp,
        StatType.STAMINA to staminaXp,
    ),
)

private fun PlayerProfileEntity.toDomain(): PlayerProfile {
    val snapshot = snapshot()
    return PlayerProfile(
        totalXp = totalXp,
        level = LevelCurve.CHARACTER.progressFor(totalXp),
        stats = StatType.entries.map { stat ->
            val xp = snapshot.statXp.getValue(stat)
            StatProgress(stat, xp, LevelCurve.STAT.progressFor(xp))
        },
        questsCompleted = questsCompleted,
    )
}
