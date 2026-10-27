package com.ironquest.app.domain.repository

import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.ExerciseImport
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Training programs: the built-in split (ProgramCatalog, `id == null`) plus programs imported
 * from CSV. Exactly one is active; none flagged in the database means the built-in one.
 */
interface ProgramRepository {
    /**
     * The program whose days rotate on Today (it has at least one day), or null when the
     * built-in program was deleted and no imported one is left.
     */
    fun observeActiveProgram(): Flow<Program?>

    /** Built-in first (unless deleted), then imported programs in import order. */
    fun observePrograms(): Flow<List<Program>>

    /** True while the built-in program is deleted; [restoreBuiltIn] brings it back. */
    fun observeBuiltInHidden(): Flow<Boolean>

    /** Any template by id — built-in or imported, active or not. Null if it no longer exists. */
    suspend fun findTemplate(templateId: String): WorkoutTemplate?

    /** Creates a program from [days] and makes it active. Returns its id. */
    suspend fun importProgram(name: String, days: List<DayImport>): Long

    /** Appends [days] to an imported program. */
    suspend fun addDays(programId: Long, days: List<DayImport>)

    /**
     * Appends [exercises] to an imported day. Throws [ProgramException] ([ProgramProblem.AlreadyInDay])
     * naming any exercise the day already has.
     */
    suspend fun addExercises(templateId: String, exercises: List<ExerciseImport>)

    /** null activates the built-in program (throws [ProgramException] while it is deleted). */
    suspend fun setActiveProgram(id: Long?)

    /**
     * Deletes a program (history stays); null deletes the built-in one, which [restoreBuiltIn]
     * can bring back. Throws [ProgramException] while one of its days has an unfinished
     * session. When the deleted program was active, the newest remaining one becomes active.
     */
    suspend fun deleteProgram(id: Long?)

    /** Un-deletes the built-in program. It does not become active by itself. */
    suspend fun restoreBuiltIn()
}
