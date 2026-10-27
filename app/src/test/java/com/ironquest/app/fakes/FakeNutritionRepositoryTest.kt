package com.ironquest.app.fakes

import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.nutrition.SupplementStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNutritionRepositoryTest {

    private val today = FakeNutritionRepository.TODAY
    private val past = today.minusDays(2)

    @Test
    fun `a supplement-only day keeps a row in history`() = runTest {
        val repo = FakeNutritionRepository()
        repo.supplements.value = listOf(SupplementStatus(1, "Creatine", "5 g", taken = false, streak = 0))

        assertNull(repo.observeHistory(past, today).first().rows[past])
        repo.setSupplementTaken(past, 1, true)

        val history = repo.observeHistory(past, today).first()
        assertTrue(past in history.rows)
        assertTrue(history.totals.isEmpty())
    }

    @Test
    fun `un-logging every meal leaves the day's row behind`() = runTest {
        val repo = FakeNutritionRepository()
        repo.meals.value = listOf(Meal(1, "Oats", Macros(proteinG = 10.0), emptyList(), null, null))
        repo.logOffPlan(past, 1, 1.0)
        val logId = repo.logs.value.getValue(past).single().id

        repo.removeLog(logId)

        val history = repo.observeHistory(past, today).first()
        assertTrue(past in history.rows)
        assertNull(history.totals[past])
    }

    @Test
    fun `updatePortion on a missing log is alreadyDone, like removeLog`() = runTest {
        val repo = FakeNutritionRepository()
        val award = repo.updatePortion(logId = 42, portion = 2.0)
        assertTrue(award.alreadyDone)
        assertEquals(0, award.xpDelta)
    }

    @Test
    fun `addWater clamps at the daily cap`() = runTest {
        val repo = FakeNutritionRepository()
        repo.addWater(today, 9_900)
        repo.addWater(today, 250)
        assertEquals(NutritionRules.MAX_WATER_DAY_ML, repo.observeDay(today).first().waterMl)
    }

    @Test
    fun `an archived meal is listed separately and can be restored`() = runTest {
        val repo = FakeNutritionRepository()
        val oats = Meal(1, "Oats", Macros(proteinG = 10.0), emptyList(), null, null)
        repo.meals.value = listOf(oats)
        repo.plans.value = listOf(MealPlan(10, "Cut", false, MacroTargets(), listOf(PlanSlot(21, "Breakfast", 1, "Oats", oats.macros, null, null))))

        repo.deleteMeal(1)
        assertTrue(checkNotNull(repo.observeMeal(1).first()).archived)
        assertEquals(listOf(1L), repo.observeArchivedMeals().first().map { it.id })
        assertTrue(repo.observeMeals().first().isEmpty())

        repo.restoreMeal(1)
        assertFalse(checkNotNull(repo.observeMeal(1).first()).archived)
        assertEquals(listOf(1L), repo.observeMeals().first().map { it.id })
        assertTrue(repo.observeArchivedMeals().first().isEmpty())
    }

    private val oats = MealLogItem("b:oats", "Oats", 50.0, FoodUnit.GRAM, Macros(proteinG = 6.0, carbsG = 30.0, fatG = 3.0, kcal = 180.0))
    private val milk = MealLogItem("b:milk", "Milk", 200.0, FoodUnit.MILLILITRE, Macros(proteinG = 7.0, carbsG = 10.0, fatG = 4.0, kcal = 100.0))

    @Test
    fun `a new quick meal pays meal XP and stores summed macros with its items`() = runTest {
        val repo = FakeNutritionRepository()

        val award = repo.saveQuickMeal(today, null, "Breakfast", listOf(oats, milk))

        assertEquals(NutritionRules.MEAL_XP, award.xpDelta)
        val log = repo.logs.value.getValue(today).single()
        assertEquals(2, log.items.size)
        assertEquals(13.0, log.macros1x.proteinG, 0.001)
        assertEquals(log, repo.getLog(log.id))
    }

    @Test
    fun `rewriting a quick meal pays nothing and replaces the items`() = runTest {
        val repo = FakeNutritionRepository()
        repo.saveQuickMeal(today, null, "Breakfast", listOf(oats, milk))
        val logId = repo.logs.value.getValue(today).single().id

        val award = repo.saveQuickMeal(today, logId, "Breakfast", listOf(oats))

        assertEquals(0, award.xpDelta)
        assertEquals(listOf(oats), repo.getLog(logId)?.items)
    }

    @Test
    fun `rewriting a deleted quick meal is alreadyDone`() = runTest {
        val repo = FakeNutritionRepository()
        assertTrue(repo.saveQuickMeal(today, 42, "Ghost", listOf(oats)).alreadyDone)
    }

    @Test
    fun `a failing quick meal save throws once and stores nothing`() = runTest {
        val repo = FakeNutritionRepository()
        repo.failNext = IllegalStateException("boom")
        try {
            repo.saveQuickMeal(today, null, "Breakfast", listOf(oats))
        } catch (expected: IllegalStateException) {
            assertTrue(repo.logs.value.isEmpty())
            return@runTest
        }
        throw AssertionError("expected failure")
    }
}
