package com.ironquest.app.ui.food

import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.NutritionRules
import kotlin.math.roundToLong

/** One ingredient in the calculator sheet. Never persisted: only the totals reach the meal. */
data class IngredientRow(val key: Long, val food: FoodChoice, val unit: FoodUnit, val amountText: String) {
    val amount: Double? get() = FoodAmount.parse(amountText, unit)

    /** Macros for the amount of the food, kcal resolved. Null while the amount text is invalid. */
    val macros: Macros? get() = amount?.let { FoodAmount.macrosFor(food.per100g, it, unit) }
}

/** Pure calculator rules, kept out of the composable so they're unit-tested. */
object IngredientCalculator {
    /**
     * Sum of all rows, or null if there are none or any row has an invalid amount.
     * kcal is null only when **no** food had an energy value, so the editor keeps deriving it from P/C/F.
     */
    fun totalOf(rows: List<IngredientRow>): Macros? {
        if (rows.isEmpty()) return null
        val scaled = rows.map { it.macros ?: return null }
        val sum = scaled.fold(Macros.ZERO) { acc, macros -> acc + macros }
        return if (rows.all { it.food.per100g.kcal == null }) sum.copy(kcal = null) else sum
    }

    /**
     * True when a total exceeds what the meal editor accepts (grams above [NutritionRules.MAX_MEAL_GRAMS],
     * kcal above [NutritionRules.MAX_KCAL]), so applying it would fail validation on save.
     */
    fun exceedsEditorLimits(total: Macros): Boolean = total.proteinG > NutritionRules.MAX_MEAL_GRAMS ||
        total.carbsG > NutritionRules.MAX_MEAL_GRAMS ||
        total.fatG > NutritionRules.MAX_MEAL_GRAMS ||
        total.fiberG > NutritionRules.MAX_MEAL_GRAMS ||
        total.effectiveKcal > NutritionRules.MAX_KCAL

    /** Overwrites only P/C/F/fiber/kcal. Grams rounded to 0.1, kcal to a whole number. */
    fun applyTo(form: MealForm, total: Macros): MealForm = form.copy(
        protein = total.proteinG.oneDecimal().formatAmount(),
        carbs = total.carbsG.oneDecimal().formatAmount(),
        fat = total.fatG.oneDecimal().formatAmount(),
        fiber = total.fiberG.oneDecimal().formatAmount(),
        kcal = total.kcal?.let { it.roundToLong().toDouble().formatAmount() }.orEmpty(),
    )

    private fun Double.oneDecimal(): Double = (this * 10).roundToLong() / 10.0
}
