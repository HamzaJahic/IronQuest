package com.ironquest.app.domain.food

import org.junit.Assert.assertEquals
import org.junit.Test

class MealNameRulesTest {
    @Test
    fun `names follow the time of day`() {
        assertEquals(QuickMealName.BREAKFAST, MealNameRules.forTime(7 * 60))
        assertEquals(QuickMealName.LUNCH, MealNameRules.forTime(10 * 60 + 30))
        assertEquals(QuickMealName.SNACK, MealNameRules.forTime(15 * 60))
        assertEquals(QuickMealName.DINNER, MealNameRules.forTime(17 * 60 + 30))
        assertEquals(QuickMealName.SNACK, MealNameRules.forTime(21 * 60 + 30))
        assertEquals(QuickMealName.BREAKFAST, MealNameRules.forTime(0))
    }

    @Test
    fun `a past day has no clock time and defaults to snack`() {
        assertEquals(QuickMealName.SNACK, MealNameRules.forTime(null))
    }
}
