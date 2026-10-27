package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.FieldValue
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.NutritionRules

/** Amount rules for one food line. "," is accepted as a decimal separator. */
object FoodAmount {
    const val MAX_GRAMS = 5_000.0
    const val MAX_COUNT = 100.0

    fun maxFor(unit: FoodUnit): Double = when (unit.kind) {
        UnitKind.GRAM, UnitKind.MILLILITRE -> MAX_GRAMS
        UnitKind.MEAL_PORTION -> NutritionRules.MAX_PORTION
        else -> MAX_COUNT
    }

    /** 0 < amount ≤ [maxFor]; null when blank or invalid. */
    fun parse(text: String, unit: FoodUnit): Double? = (NutritionRules.parseField(text, maxFor(unit)) as? FieldValue.Value)?.value?.takeIf { it > 0.0 }

    /** Pieces are what people count, so a food with a piece unit starts there; otherwise its first unit. */
    fun startUnit(choice: FoodChoice): FoodUnit = choice.units.firstOrNull { it.kind == UnitKind.PIECE } ?: choice.units.firstOrNull() ?: FoodUnit.GRAM

    /** What the amount field holds when a unit is picked: 100 for g / ml, else 1. */
    fun defaultText(unit: FoodUnit): String = when (unit.kind) {
        UnitKind.GRAM, UnitKind.MILLILITRE -> "100" // not UI text: editor field value
        else -> "1" // not UI text: editor field value
    }

    fun grams(amount: Double, unit: FoodUnit): Double = amount * unit.gramsPerUnit

    /** Resolved kcal (always non-null), like [Macros.times]. */
    fun macrosFor(per100g: Macros, amount: Double, unit: FoodUnit): Macros = per100g * (grams(amount, unit) / 100.0)
}
