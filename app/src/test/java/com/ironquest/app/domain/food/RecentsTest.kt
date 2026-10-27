package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsTest {
    private fun item(key: String, amount: Double) = MealLogItem(key, key, amount, FoodUnit.GRAM, Macros(proteinG = amount / 10, kcal = amount))
    private fun meal(id: Long, vararg items: MealLogItem) = MealLogEntry(id, slotId = null, mealId = null, mealName = "Lunch", portion = 1.0, macros1x = Macros.ZERO, items = items.toList())

    @Test
    fun `the same foods in another order count once, newest kept`() {
        val newest = meal(3, item("b:1", 100.0), item("b:2", 50.0))
        val older = meal(2, item("b:2", 50.0), item("b:1", 100.0))
        val different = meal(1, item("b:1", 150.0))
        assertEquals(listOf(3L, 1L), Recents.distinctMeals(listOf(newest, older, different), limit = 10).map { it.id })
    }

    @Test
    fun `logs without items are not quick meals`() {
        assertEquals(emptyList<Long>(), Recents.distinctMeals(listOf(meal(1)), limit = 10).map { it.id })
    }

    @Test
    fun `the limit applies after de-duplication`() {
        val meals = (10L downTo 1L).map { meal(it, item("b:$it", 100.0)) }
        assertEquals(listOf(10L, 9L, 8L), Recents.distinctMeals(meals, limit = 3).map { it.id })
    }
}
