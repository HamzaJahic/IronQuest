package com.ironquest.app.domain.importing

import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.SlotDraft
import com.ironquest.app.domain.repository.NutritionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** What an import wrote: new meals, meals that already existed (same name and macros), and the new plan. */
data class MealImportResult(val created: Int, val reused: Int, val planId: Long? = null)

/**
 * Writes parsed meal CSVs through [NutritionRepository]. A row whose name and macros match a
 * library meal (or an earlier row) reuses that meal instead of creating a duplicate.
 */
class MealImportUseCase @Inject constructor(private val repository: NutritionRepository) {

    suspend fun importMeals(meals: List<MealDraft>): MealImportResult {
        val resolver = Resolver(repository.observeMeals().first())
        meals.forEach { resolver.idFor(it) }
        return MealImportResult(resolver.created, resolver.reused)
    }

    /** Creates an inactive plan; its daily targets are the sum of its meals (water unset). */
    suspend fun importPlan(name: String, slots: List<SlotImport>): MealImportResult {
        require(slots.isNotEmpty()) { "A plan needs at least one meal" }
        val resolver = Resolver(repository.observeMeals().first())
        val slotDrafts = slots.map { slot ->
            SlotDraft(id = null, label = slot.label, mealId = resolver.idFor(slot.meal), timeOfDayMin = slot.timeOfDayMin, reminderOffsetMin = null)
        }
        val total = slots.fold(Macros.ZERO) { acc, slot -> acc + slot.meal.macros }
        val result = repository.savePlan(PlanDraft(id = null, name = name.trim(), targets = targetsFor(total), slots = slotDrafts))
        return MealImportResult(resolver.created, resolver.reused, result.planId)
    }

    private inner class Resolver(library: List<Meal>) {
        private val known = library.map { Triple(it.name, it.macros, it.id) }.toMutableList()
        var created = 0
            private set
        var reused = 0
            private set

        suspend fun idFor(draft: MealDraft): Long {
            known.firstOrNull { (name, macros) -> name.equals(draft.name, ignoreCase = true) && sameMacros(macros, draft.macros) }
                ?.let { match ->
                    reused++
                    return match.third
                }
            val id = repository.saveMeal(draft)
            created++
            known += Triple(draft.name, draft.macros, id)
            return id
        }
    }

    companion object {
        private fun close(first: Double, second: Double) = abs(first - second) < 0.05

        fun sameMacros(first: Macros, second: Macros): Boolean = close(first.proteinG, second.proteinG) &&
            close(first.carbsG, second.carbsG) &&
            close(first.fatG, second.fatG) &&
            close(first.fiberG, second.fiberG) &&
            abs(first.effectiveKcal - second.effectiveKcal) < 1.0

        fun targetsFor(total: Macros): MacroTargets {
            fun rounded(grams: Double) = (grams * 10).roundToInt().div(10.0).coerceAtMost(NutritionRules.MAX_TARGET_GRAMS).takeIf { it > 0 }
            return MacroTargets(
                proteinG = rounded(total.proteinG),
                carbsG = rounded(total.carbsG),
                fatG = rounded(total.fatG),
                fiberG = rounded(total.fiberG),
                kcal = total.effectiveKcal.roundToInt().toDouble().coerceAtMost(NutritionRules.MAX_KCAL).takeIf { it > 0 },
            )
        }
    }
}
