package com.ironquest.app.fakes

import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.ExerciseImport
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.repository.ProgramRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Always the built-in program; import writes are not modelled. */
class FakeProgramRepository : ProgramRepository {
    private val builtIn = Program(null, "", isActive = true, templates = ProgramCatalog.templates)
    private val active = MutableStateFlow(builtIn)

    override fun observeActiveProgram(): Flow<Program?> = active
    override fun observeBuiltInHidden(): Flow<Boolean> = MutableStateFlow(false)
    override fun observePrograms(): Flow<List<Program>> = MutableStateFlow(listOf(builtIn))
    override suspend fun findTemplate(templateId: String): WorkoutTemplate? = ProgramCatalog.templates.firstOrNull { it.id == templateId }
    override suspend fun importProgram(name: String, days: List<DayImport>): Long = error("not supported")
    override suspend fun addDays(programId: Long, days: List<DayImport>) = error("not supported")
    override suspend fun addExercises(templateId: String, exercises: List<ExerciseImport>) = error("not supported")
    override suspend fun setActiveProgram(id: Long?) = Unit
    override suspend fun deleteProgram(id: Long?) = Unit
    override suspend fun restoreBuiltIn() = Unit
}
