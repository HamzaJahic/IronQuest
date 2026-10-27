package com.ironquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/** Erases logged data only; programs, meals, plans and supplements are never touched here. */
@Dao
interface WipeDao {

    /** Sets go with their workouts (CASCADE). */
    @Query("DELETE FROM workouts")
    suspend fun deleteWorkouts()

    @Query("DELETE FROM meal_logs")
    suspend fun deleteMealLogs()

    @Query("DELETE FROM nutrition_days")
    suspend fun deleteNutritionDays()

    @Query("DELETE FROM supplement_intakes")
    suspend fun deleteIntakes()

    @Query(
        """
        UPDATE player_profile SET totalXp = 0, constitutionXp = 0, strengthXp = 0, staminaXp = 0,
            questsCompleted = 0, highestLevelReached = 1
        WHERE id = 1
        """,
    )
    suspend fun resetProfile()

    @Transaction
    suspend fun wipeLoggedData() {
        deleteWorkouts()
        deleteMealLogs()
        deleteNutritionDays()
        deleteIntakes()
        resetProfile()
    }
}
