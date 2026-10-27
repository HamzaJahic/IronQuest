package com.ironquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ironquest.app.data.local.entity.ExerciseEntity
import com.ironquest.app.data.local.entity.ProgramDayEntity
import com.ironquest.app.data.local.entity.ProgramEntity
import com.ironquest.app.data.local.entity.ProgramExerciseEntity
import com.ironquest.app.data.local.entity.ProgramExerciseRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {

    /** Every imported program with its days and exercises, in display order. */
    @Query(
        """
        SELECT p.id AS programId, p.name AS programName, p.isActive AS programActive,
               d.id AS dayId, d.name AS dayName, d.position AS dayPosition,
               e.id AS exerciseId, e.name AS exerciseName, e.stat, e.trackingType, e.incrementKg,
               x.targetSets, x.repMin, x.repMax, x.position, x.supersetGroup
        FROM programs p
        LEFT JOIN program_days d ON d.programId = p.id
        LEFT JOIN program_exercises x ON x.dayId = d.id
        LEFT JOIN exercises e ON e.id = x.exerciseId
        ORDER BY p.id, d.position, d.id, x.position, x.id
        """,
    )
    fun observeAll(): Flow<List<ProgramExerciseRow>>

    /** Same rows for the program that owns [dayId]. */
    @Query(
        """
        SELECT p.id AS programId, p.name AS programName, p.isActive AS programActive,
               d.id AS dayId, d.name AS dayName, d.position AS dayPosition,
               e.id AS exerciseId, e.name AS exerciseName, e.stat, e.trackingType, e.incrementKg,
               x.targetSets, x.repMin, x.repMax, x.position, x.supersetGroup
        FROM program_days d
        INNER JOIN programs p ON p.id = d.programId
        LEFT JOIN program_exercises x ON x.dayId = d.id
        LEFT JOIN exercises e ON e.id = x.exerciseId
        WHERE d.id = :dayId
        ORDER BY x.position, x.id
        """,
    )
    suspend fun dayRows(dayId: Long): List<ProgramExerciseRow>

    @Insert
    suspend fun insertProgram(program: ProgramEntity): Long

    @Insert
    suspend fun insertDay(day: ProgramDayEntity): Long

    @Insert
    suspend fun insertExercises(rows: List<ProgramExerciseEntity>)

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getProgram(id: Long): ProgramEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM program_days WHERE programId = :programId")
    suspend fun maxDayPosition(programId: Long): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM program_exercises WHERE dayId = :dayId")
    suspend fun maxExercisePosition(dayId: Long): Int

    @Query("SELECT exerciseId FROM program_exercises WHERE dayId = :dayId")
    suspend fun exerciseIds(dayId: Long): List<String>

    @Query("SELECT DISTINCT supersetGroup FROM program_exercises WHERE dayId = :dayId AND supersetGroup IS NOT NULL")
    suspend fun supersetGroups(dayId: Long): List<String>

    @Query("SELECT id FROM program_days WHERE programId = :programId")
    suspend fun dayIds(programId: Long): List<Long>

    @Query("UPDATE programs SET isActive = 0")
    suspend fun clearActive()

    @Query("UPDATE programs SET isActive = 1 WHERE id = :id")
    suspend fun activate(id: Long): Int

    @Query("SELECT id FROM programs ORDER BY id DESC LIMIT 1")
    suspend fun latestProgramId(): Long?

    @Query("SELECT COUNT(*) FROM programs WHERE isActive = 1")
    suspend fun activeCount(): Int

    @Query("DELETE FROM programs WHERE id = :id")
    suspend fun deleteProgram(id: Long)

    @Query("SELECT COUNT(*) FROM workouts WHERE completedAtMillis IS NULL AND templateId IN (:templateIds)")
    suspend fun countInProgress(templateIds: List<String>): Int

    /**
     * The first import of an exercise defines it; later imports reuse the row unchanged, so a
     * re-import can't flip an exercise with history from reps to minutes. Never REPLACE (see SeedCallback).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExerciseIfMissing(exercise: ExerciseEntity): Long
}
