package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextMealTest {

    private val macros = Macros(proteinG = 10.0)
    private fun slot(id: Long, time: Int?) = PlanSlot(id, "S$id", 1, "Meal $id", macros, time, null)
    private fun plan(vararg slots: PlanSlot) = MealPlan(1, "Cut", true, MacroTargets(), slots.toList())
    private fun logged(slotId: Long) = MealLogEntry(slotId * 100, slotId, 1, "x", 1.0, macros)

    @Test
    fun `picks the earliest unchecked slot at or after now`() {
        val plan = plan(slot(1, 450), slot(2, 1200), slot(3, 780))
        assertEquals(3L, NextMeal.pick(plan, emptyList(), nowMin = 600)?.id)
        assertEquals(3L, NextMeal.pick(plan, emptyList(), nowMin = 780)?.id) // inclusive
    }

    @Test
    fun `checked slots are skipped`() {
        val plan = plan(slot(1, 450), slot(2, 780))
        assertEquals(2L, NextMeal.pick(plan, listOf(logged(1)), nowMin = 0)?.id)
    }

    @Test
    fun `when nothing is upcoming the first unchecked slot in plan order is next`() {
        val plan = plan(slot(1, 450), slot(2, null), slot(3, 780))
        assertEquals(1L, NextMeal.pick(plan, emptyList(), nowMin = 1000)?.id)
        assertEquals(2L, NextMeal.pick(plan, listOf(logged(1)), nowMin = 1000)?.id)
    }

    @Test
    fun `untimed slots wait while a timed one is upcoming`() {
        val plan = plan(slot(1, null), slot(2, 780))
        assertEquals(2L, NextMeal.pick(plan, emptyList(), nowMin = 600)?.id)
    }

    @Test
    fun `nothing left or no plan gives null`() {
        assertNull(NextMeal.pick(plan(slot(1, 450)), listOf(logged(1)), nowMin = 0))
        assertNull(NextMeal.pick(null, emptyList(), nowMin = 0))
    }

    @Test
    fun `an active plan with no slots gives null`() {
        assertNull(NextMeal.pick(plan(), emptyList(), nowMin = 0))
    }
}
