package com.ironquest.app.domain.food

enum class QuickMealName { BREAKFAST, LUNCH, DINNER, SNACK }

object MealNameRules {
    /** [minuteOfDay] null = a past day (no meaningful clock time). */
    fun forTime(minuteOfDay: Int?): QuickMealName = when {
        minuteOfDay == null -> QuickMealName.SNACK
        minuteOfDay < 10 * 60 + 30 -> QuickMealName.BREAKFAST
        minuteOfDay < 15 * 60 -> QuickMealName.LUNCH
        minuteOfDay < 17 * 60 + 30 -> QuickMealName.SNACK
        minuteOfDay < 21 * 60 + 30 -> QuickMealName.DINNER
        else -> QuickMealName.SNACK
    }
}
