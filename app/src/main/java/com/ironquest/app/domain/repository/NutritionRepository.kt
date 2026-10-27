package com.ironquest.app.domain.repository

import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.MealUsage
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.NutritionHistory
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.ReminderSnapshot
import com.ironquest.app.domain.nutrition.SavePlanResult
import com.ironquest.app.domain.nutrition.SupplementDraft
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Meals, plans and the daily food log. Every method returning [NutritionAward] pays or
 * reverses character XP atomically with its write. Writes for dates after today throw
 * [IllegalArgumentException].
 */
interface NutritionRepository {
    /** Non-archived meals, sorted by name. */
    fun observeMeals(): Flow<List<Meal>>

    /** Archived meals only, sorted by name. */
    fun observeArchivedMeals(): Flow<List<Meal>>

    /** Any meal by id, archived included (plans and logs may still point at it). */
    fun observeMeal(id: Long): Flow<Meal?>

    fun observeMealUsages(mealId: Long): Flow<List<MealUsage>>

    /** Only the name is validated here; macro ranges and URL normalisation are the caller's job. */
    suspend fun saveMeal(draft: MealDraft): Long

    /** Archives a meal used by any plan slot; hard-deletes it otherwise. */
    suspend fun deleteMeal(id: Long): DeleteOutcome

    /** Un-archives a meal. A missing or non-archived meal is a no-op. */
    suspend fun restoreMeal(id: Long)

    fun observePlans(): Flow<List<MealPlan>>

    fun observePlan(id: Long): Flow<MealPlan?>

    /**
     * Editing the active plan's targets re-evaluates today's bonuses; that XP change is applied
     * and returned in [SavePlanResult.award] ([NutritionAward.NONE] for an inactive plan).
     *
     * Updates existing slots in place (keeps ids), inserts new ones, deletes removed ones. */
    suspend fun savePlan(draft: PlanDraft): SavePlanResult

    /** Activates exactly one plan, or none when [id] is null. Re-evaluates today's bonuses. */
    suspend fun setActivePlan(id: Long?): NutritionAward

    /** Inactive copy of plan [id]; [copyName] turns the original name into the copy's (the caller supplies the translated wording). */
    suspend fun duplicatePlan(id: Long, copyName: (String) -> String): Long

    suspend fun deletePlan(id: Long): NutritionAward

    fun observeDay(date: LocalDate): Flow<DayNutrition>

    /** Idempotent: a slot already logged for [date] returns `alreadyDone = true` and pays nothing. */
    suspend fun logSlot(date: LocalDate, slotId: Long, portion: Double): NutritionAward

    suspend fun logOffPlan(date: LocalDate, mealId: Long, portion: Double): NutritionAward

    /**
     * Creates ([logId] null) or rewrites a quick meal on [date]: one meal_logs row (1×, macros = sum of
     * [items]) plus its items. New pays [com.ironquest.app.domain.nutrition.NutritionRules.MEAL_XP]; a
     * rewrite pays nothing itself, but the day's protein bonus is re-evaluated either way. A rewrite of
     * a deleted log is `alreadyDone`.
     */
    suspend fun saveQuickMeal(date: LocalDate, logId: Long?, name: String, items: List<MealLogItem>): NutritionAward

    /** One log with its items, for the builder's edit mode. */
    suspend fun getLog(logId: Long): MealLogEntry?

    /** A missing log returns `alreadyDone = true`, like [removeLog]. */
    suspend fun updatePortion(logId: Long, portion: Double): NutritionAward

    suspend fun removeLog(logId: Long): NutritionAward

    /** [deltaMl] may be negative; the total stays within 0..[com.ironquest.app.domain.nutrition.NutritionRules.MAX_WATER_DAY_ML] (see NutritionRules.clampWater). */
    suspend fun addWater(date: LocalDate, deltaMl: Int): NutritionAward

    /** Idempotent in both directions. */
    suspend fun setSupplementTaken(date: LocalDate, supplementId: Long, taken: Boolean): NutritionAward

    suspend fun saveSupplement(draft: SupplementDraft): Long

    /** Archives a supplement with any intakes (keeps history and XP); hard-deletes it otherwise. */
    suspend fun deleteSupplement(id: Long): DeleteOutcome

    /**
     * Raw per-day data for [from]..[to] inclusive, re-emitted on every change. Read-only. The
     * "today = live plan, past = snapshot" rule and all aggregation live in
     * NutritionOverviewCalculator, not here.
     */
    fun observeHistory(from: LocalDate, to: LocalDate): Flow<NutritionHistory>

    /**
     * One-shot read for the reminder scheduler: the active plan's slots,
     * non-archived supplements, and which slots were logged / supplements taken on each
     * day from [from] to [to] inclusive.
     */
    suspend fun reminderSnapshot(from: LocalDate, to: LocalDate): ReminderSnapshot
}
