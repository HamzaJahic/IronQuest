package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import org.junit.Assert.assertEquals
import org.junit.Test

class MealLibraryViewModelTest {

    private val meals = listOf(
        Meal(1, "Oats with whey", Macros(), listOf("breakfast", "high-protein"), null, null),
        Meal(2, "Chicken & rice bowl", Macros(), listOf("lunch", "high-protein"), null, null),
        Meal(3, "Tuna wrap", Macros(), listOf("lunch", "quick"), null, null),
    )

    private fun ids(query: String, tag: String?) = MealLibraryViewModel.filterMeals(meals, query, tag).map { it.id }

    @Test
    fun `empty query and no tag returns everything`() = assertEquals(listOf(1L, 2L, 3L), ids("", null))

    @Test
    fun `query matches name or tag, case-insensitive`() {
        assertEquals(listOf(2L), ids("CHICK", null))
        assertEquals(listOf(3L), ids("quick", null))
    }

    @Test
    fun `tag filter combines with the query`() {
        assertEquals(listOf(2L, 3L), ids("", "lunch"))
        assertEquals(listOf(3L), ids("tuna", "lunch"))
        assertEquals(emptyList<Long>(), ids("oats", "lunch"))
    }
}
