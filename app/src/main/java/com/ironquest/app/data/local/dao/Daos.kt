package com.ironquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ironquest.app.data.local.entity.LastCompletionRow
import com.ironquest.app.data.local.entity.PlayerProfileEntity
import com.ironquest.app.data.local.entity.SetDetailRow
import com.ironquest.app.data.local.entity.SetEntity
import com.ironquest.app.data.local.entity.SetStatXp
import com.ironquest.app.data.local.entity.WorkoutEntity
import com.ironquest.app.data.local.entity.WorkoutWithSets
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE templateId = :templateId AND completedAtMillis IS NULL
        ORDER BY startedAtMillis DESC LIMIT 1
        """,
    )
    suspend fun findInProgressWithSets(templateId: String): WorkoutWithSets?

    @Query("SELECT * FROM workouts WHERE completedAtMillis IS NULL ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeAnyInProgress(): Flow<WorkoutEntity?>

    /** Ghost Mode source: the most recent *completed* session of this template. */
    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE templateId = :templateId AND completedAtMillis IS NOT NULL
        ORDER BY completedAtMillis DESC LIMIT 1
        """,
    )
    fun observeLastCompletedWithSets(templateId: String): Flow<WorkoutWithSets?>

    /** Same as [observeLastCompletedWithSets], read once (inside completeWorkout's transaction). */
    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE templateId = :templateId AND completedAtMillis IS NOT NULL
        ORDER BY completedAtMillis DESC LIMIT 1
        """,
    )
    suspend fun getLastCompletedWithSets(templateId: String): WorkoutWithSets?

    /** The latest completed session of the template that started before [startedBeforeMillis] (a past entry's "last time"). */
    @Transaction
    @Query(
        """
        SELECT * FROM workouts
        WHERE templateId = :templateId AND completedAtMillis IS NOT NULL
          AND startedAtMillis < :startedBeforeMillis AND id != :excludeId
        ORDER BY completedAtMillis DESC LIMIT 1
        """,
    )
    suspend fun getLastCompletedBefore(templateId: String, startedBeforeMillis: Long, excludeId: Long): WorkoutWithSets?

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT templateId, completedAtMillis FROM workouts WHERE completedAtMillis IS NOT NULL ORDER BY completedAtMillis DESC LIMIT 1")
    fun observeLastCompletion(): Flow<LastCompletionRow?>

    @Query(
        """
        SELECT DISTINCT dateEpochDay FROM workouts
        WHERE completedAtMillis IS NOT NULL AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeCompletedDays(fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>

    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE completedAtMillis IS NOT NULL AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeCompletedCount(fromEpochDay: Long, toEpochDay: Long): Flow<Int>

    /** One row per completed workout (start date), for the week streak. */
    @Query(
        """
        SELECT dateEpochDay FROM workouts
        WHERE completedAtMillis IS NOT NULL AND dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
        """,
    )
    fun observeCompletedDates(fromEpochDay: Long, toEpochDay: Long): Flow<List<Long>>
}

@Dao
interface SetDao {

    @Insert
    suspend fun insert(set: SetEntity): Long

    @Query("SELECT * FROM sets WHERE id = :id")
    suspend fun getById(id: Long): SetEntity?

    @Query("UPDATE sets SET reps = :reps, weightKg = :weightKg, durationMinutes = :durationMinutes WHERE id = :id")
    suspend fun updatePerformance(id: Long, reps: Int, weightKg: Double, durationMinutes: Int?)

    @Query("SELECT * FROM sets WHERE workoutId = :workoutId ORDER BY exerciseId, setIndex")
    suspend fun getForWorkout(workoutId: Long): List<SetEntity>

    @Query("SELECT COUNT(*) FROM sets WHERE workoutId = :workoutId")
    suspend fun countForWorkout(workoutId: Long): Int

    /** Everything the calendar bottom sheet needs for one day, in display order. */
    @Query(
        """
        SELECT w.id AS workoutId, w.templateId, w.dateEpochDay, w.completedAtMillis, w.xpEarned AS workoutXp,
               e.id AS exerciseId, e.name AS exerciseName, e.stat, e.trackingType,
               s.setIndex, s.reps, s.weightKg, s.durationMinutes, s.id AS setId,
               s.targetRepMin, s.targetRepMax
        FROM sets s
        INNER JOIN workouts w ON w.id = s.workoutId
        INNER JOIN exercises e ON e.id = s.exerciseId
        WHERE w.dateEpochDay = :epochDay AND w.completedAtMillis IS NOT NULL
        ORDER BY w.completedAtMillis, s.id
        """,
    )
    fun observeCompletedSetDetailsForDay(epochDay: Long): Flow<List<SetDetailRow>>

    /** The same rows for one completed workout (the editor for a past session). */
    @Query(
        """
        SELECT w.id AS workoutId, w.templateId, w.dateEpochDay, w.completedAtMillis, w.xpEarned AS workoutXp,
               e.id AS exerciseId, e.name AS exerciseName, e.stat, e.trackingType,
               s.setIndex, s.reps, s.weightKg, s.durationMinutes, s.id AS setId,
               s.targetRepMin, s.targetRepMax
        FROM sets s
        INNER JOIN workouts w ON w.id = s.workoutId
        INNER JOIN exercises e ON e.id = s.exerciseId
        WHERE w.id = :workoutId AND w.completedAtMillis IS NOT NULL
        ORDER BY s.id
        """,
    )
    suspend fun getCompletedSetDetails(workoutId: Long): List<SetDetailRow>

    @Query("DELETE FROM sets WHERE workoutId = :workoutId")
    suspend fun deleteForWorkout(workoutId: Long)

    @Query("SELECT s.xpAwarded AS xpAwarded, e.stat AS stat FROM sets s INNER JOIN exercises e ON e.id = s.exerciseId WHERE s.workoutId = :workoutId")
    suspend fun statXpForWorkout(workoutId: Long): List<SetStatXp>

    @Query("SELECT s.xpAwarded AS xpAwarded, e.stat AS stat FROM sets s INNER JOIN exercises e ON e.id = s.exerciseId WHERE s.id = :id")
    suspend fun statXpForSet(id: Long): SetStatXp?

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlayerProfileDao {

    @Query("SELECT * FROM player_profile WHERE id = 1")
    fun observe(): Flow<PlayerProfileEntity?>

    @Query("SELECT * FROM player_profile WHERE id = 1")
    suspend fun get(): PlayerProfileEntity?

    @Query(
        """
        UPDATE player_profile SET
            totalXp = totalXp + :totalXp,
            constitutionXp = constitutionXp + :constitutionXp,
            strengthXp = strengthXp + :strengthXp,
            staminaXp = staminaXp + :staminaXp
        WHERE id = 1
        """,
    )
    suspend fun addXp(totalXp: Int, constitutionXp: Int, strengthXp: Int, staminaXp: Int)

    @Query("UPDATE player_profile SET questsCompleted = questsCompleted + 1 WHERE id = 1")
    suspend fun incrementQuestsCompleted()

    /** Edits and deletions of past workouts: deltas may be negative; nothing goes below 0. */
    @Query(
        """
        UPDATE player_profile SET
            totalXp = MAX(0, totalXp + :totalXp),
            constitutionXp = MAX(0, constitutionXp + :constitutionXp),
            strengthXp = MAX(0, strengthXp + :strengthXp),
            staminaXp = MAX(0, staminaXp + :staminaXp)
        WHERE id = 1
        """,
    )
    suspend fun adjustXp(totalXp: Int, constitutionXp: Int, strengthXp: Int, staminaXp: Int)

    @Query("UPDATE player_profile SET questsCompleted = MAX(0, questsCompleted + :delta) WHERE id = 1")
    suspend fun adjustQuestsCompleted(delta: Int)

    /** Nutrition XP is character-only and can be negative (un-logging); floored at 0. */
    @Query("UPDATE player_profile SET totalXp = MAX(0, totalXp + :delta) WHERE id = 1")
    suspend fun addCharacterXp(delta: Int)

    @Query("UPDATE player_profile SET highestLevelReached = MAX(highestLevelReached, :level) WHERE id = 1")
    suspend fun raiseHighestLevel(level: Int)
}
