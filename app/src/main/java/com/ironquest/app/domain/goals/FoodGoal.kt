package com.ironquest.app.domain.goals

import com.ironquest.app.domain.nutrition.NutritionRules

enum class FoodTarget { PROTEIN, WATER }

/** Today's protein (g) or water (ml) target of the active plan. */
data class FoodGoal(val target: FoodTarget, val amount: Double, val goal: Double) : Goal {
    override val type get() = GoalType.FOOD
    override val bonusXp get() = 0

    val remaining: Double get() = (goal - amount).coerceAtLeast(0.0)

    /** Same thresholds as [NutritionRules.proteinBonus] / [NutritionRules.waterBonus]. */
    val isDone: Boolean get() = amount >= goal - EPS

    /** The nutrition bonus already paid for reaching it (shown as a hint, not paid again). */
    val nutritionBonusXp: Int
        get() = when (target) {
            FoodTarget.PROTEIN -> NutritionRules.PROTEIN_BONUS_XP
            FoodTarget.WATER -> NutritionRules.WATER_BONUS_XP
        }

    private companion object {
        const val EPS = 1e-9
    }
}
