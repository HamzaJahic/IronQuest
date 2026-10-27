package com.ironquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ironquest.app.data.local.entity.DayTotalsRow
import com.ironquest.app.data.local.entity.MealEntity
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealPlanEntity
import com.ironquest.app.data.local.entity.MealTagEntity
import com.ironquest.app.data.local.entity.MealWithTags
import com.ironquest.app.data.local.entity.NutritionDayEntity
import com.ironquest.app.data.local.entity.PlanSlotEntity
import com.ironquest.app.data.local.entity.PlanWithSlots
import com.ironquest.app.data.local.entity.SlotLogKeyRow
import com.ironquest.app.data.local.entity.SlotUsageRow
import com.ironquest.app.data.local.entity.SupplementEntity
import com.ironquest.app.data.local.entity.SupplementIntakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {

    @Transaction
    @Query("SELECT * FROM meals WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActiveMeals(): Flow<List<MealWithTags>>

    @Transaction
    @Query("SELECT * FROM meals WHERE archived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchivedMeals(): Flow<List<MealWithTags>>

    @Transaction
    @Query("SELECT * FROM meals WHERE id = :id")
    fun observeMeal(id: Long): Flow<MealWithTags?>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun getMeal(id: Long): MealEntity?

    @Insert
    suspend fun insertMeal(meal: MealEntity): Long

    @Update
    suspend fun updateMeal(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)

    @Query("UPDATE meals SET archived = 1 WHERE id = :id")
    suspend fun archiveMeal(id: Long)

    @Query("UPDATE meals SET archived = 0 WHERE id = :id")
    suspend fun restoreMeal(id: Long)

    @Query("SELECT COUNT(*) FROM plan_slots WHERE mealId = :mealId")
    suspend fun countSlotsUsingMeal(mealId: Long): Int

    @Query("DELETE FROM meal_tags WHERE mealId = :mealId")
    suspend fun clearTags(mealId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<MealTagEntity>)

    @Query(
        """
        SELECT p.name AS planName, s.label AS label, s.timeOfDayMin AS timeOfDayMin
        FROM plan_slots s INNER JOIN meal_plans p ON p.id = s.planId
        WHERE s.mealId = :mealId
        ORDER BY p.position, s.position
        """,
    )
    fun observeSlotUsages(mealId: Long): Flow<List<SlotUsageRow>>

    @Transaction
    @Query("SELECT * FROM meal_plans ORDER BY position, id")
    fun observePlans(): Flow<List<PlanWithSlots>>

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE id = :id")
    fun observePlan(id: Long): Flow<PlanWithSlots?>

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE isActive = 1 LIMIT 1")
    fun observeActivePlan(): Flow<PlanWithSlots?>

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE id = :id")
    suspend fun getPlanWithSlots(id: Long): PlanWithSlots?

    @Query("SELECT * FROM meal_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlan(): MealPlanEntity?

    @Query("SELECT * FROM meal_plans WHERE id = :id")
    suspend fun getPlan(id: Long): MealPlanEntity?

    @Insert
    suspend fun insertPlan(plan: MealPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: MealPlanEntity)

    @Query("DELETE FROM meal_plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("UPDATE meal_plans SET isActive = 0")
    suspend fun deactivateAllPlans()

    @Query("UPDATE meal_plans SET isActive = 1 WHERE id = :id")
    suspend fun activatePlan(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM meal_plans")
    suspend fun nextPlanPosition(): Int

    @Query("SELECT * FROM plan_slots WHERE planId = :planId ORDER BY position")
    suspend fun slotsForPlan(planId: Long): List<PlanSlotEntity>

    @Query("SELECT * FROM plan_slots WHERE id = :id")
    suspend fun getSlot(id: Long): PlanSlotEntity?

    @Insert
    suspend fun insertSlot(slot: PlanSlotEntity): Long

    @Update
    suspend fun updateSlot(slot: PlanSlotEntity)

    @Query("DELETE FROM plan_slots WHERE id IN (:ids)")
    suspend fun deleteSlots(ids: List<Long>)

    @Query("SELECT * FROM meal_logs WHERE dateEpochDay = :day ORDER BY loggedAtMillis, id")
    fun observeLogs(day: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE id = :id")
    suspend fun getLog(id: Long): MealLogEntity?

    @Query("SELECT * FROM meal_logs WHERE dateEpochDay = :day AND slotId = :slotId")
    suspend fun findSlotLog(day: Long, slotId: Long): MealLogEntity?

    @Insert
    suspend fun insertLog(log: MealLogEntity): Long

    @Update
    suspend fun updateLog(log: MealLogEntity)

    @Query("UPDATE meal_logs SET portion = :portion WHERE id = :id")
    suspend fun updateLogPortion(id: Long, portion: Double)

    @Query("DELETE FROM meal_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT COALESCE(SUM(proteinG * portion), 0) FROM meal_logs WHERE dateEpochDay = :day")
    suspend fun sumProtein(day: Long): Double

    @Query("SELECT * FROM nutrition_days WHERE dateEpochDay = :day")
    fun observeDay(day: Long): Flow<NutritionDayEntity?>

    @Query("SELECT * FROM nutrition_days WHERE dateEpochDay = :day")
    suspend fun getDay(day: Long): NutritionDayEntity?

    @Insert
    suspend fun insertDay(day: NutritionDayEntity)

    @Update
    suspend fun updateDay(day: NutritionDayEntity)

    @Query("SELECT * FROM supplements WHERE archived = 0 ORDER BY position, id")
    fun observeSupplements(): Flow<List<SupplementEntity>>

    /** Bounded (callers pass NutritionRules.streakWindowStart) so streak reads don't grow forever. */
    @Query("SELECT * FROM supplement_intakes WHERE dateEpochDay BETWEEN :fromDay AND :toDay")
    fun observeIntakesBetween(fromDay: Long, toDay: Long): Flow<List<SupplementIntakeEntity>>

    @Query("SELECT * FROM supplements WHERE id = :id")
    suspend fun getSupplement(id: Long): SupplementEntity?

    @Insert
    suspend fun insertSupplement(supplement: SupplementEntity): Long

    @Update
    suspend fun updateSupplement(supplement: SupplementEntity)

    @Query("DELETE FROM supplements WHERE id = :id")
    suspend fun deleteSupplement(id: Long)

    @Query("UPDATE supplements SET archived = 1 WHERE id = :id")
    suspend fun archiveSupplement(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM supplements")
    suspend fun nextSupplementPosition(): Int

    @Query("SELECT COUNT(*) FROM supplement_intakes WHERE supplementId = :id")
    suspend fun countIntakes(id: Long): Int

    @Query("SELECT * FROM supplement_intakes WHERE supplementId = :id AND dateEpochDay = :day")
    suspend fun getIntake(id: Long, day: Long): SupplementIntakeEntity?

    @Insert
    suspend fun insertIntake(intake: SupplementIntakeEntity)

    @Query("DELETE FROM supplement_intakes WHERE supplementId = :id AND dateEpochDay = :day")
    suspend fun deleteIntake(id: Long, day: Long)

    /** Logs store 1× macros and resolved kcal, so portion × value sums are exact. */
    @Query(
        """
        SELECT dateEpochDay AS dateEpochDay,
               COUNT(*) AS logCount,
               SUM(proteinG * portion) AS proteinG,
               SUM(carbsG * portion) AS carbsG,
               SUM(fatG * portion) AS fatG,
               SUM(fiberG * portion) AS fiberG,
               SUM(kcal * portion) AS kcal
        FROM meal_logs
        WHERE dateEpochDay BETWEEN :from AND :to
        GROUP BY dateEpochDay
        """,
    )
    fun observeDayTotals(from: Long, to: Long): Flow<List<DayTotalsRow>>

    @Query("SELECT * FROM nutrition_days WHERE dateEpochDay BETWEEN :from AND :to")
    fun observeDaysBetween(from: Long, to: Long): Flow<List<NutritionDayEntity>>

    @Transaction
    @Query("SELECT * FROM meal_plans WHERE isActive = 1 LIMIT 1")
    suspend fun getActivePlanWithSlots(): PlanWithSlots?

    @Query("SELECT * FROM supplements WHERE archived = 0 ORDER BY position, id")
    suspend fun getActiveSupplements(): List<SupplementEntity>

    @Query(
        """
        SELECT dateEpochDay, slotId FROM meal_logs
        WHERE slotId IS NOT NULL AND dateEpochDay BETWEEN :fromDay AND :toDay
        """,
    )
    suspend fun slotLogKeys(fromDay: Long, toDay: Long): List<SlotLogKeyRow>

    @Query("SELECT * FROM supplement_intakes WHERE dateEpochDay BETWEEN :fromDay AND :toDay")
    suspend fun intakesBetween(fromDay: Long, toDay: Long): List<SupplementIntakeEntity>
}
