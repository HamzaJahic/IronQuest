package com.ironquest.app.data.repository

import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanDraft
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.fakes.FakeNutritionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RearmingNutritionRepositoryTest {

    private val today = FakeNutritionRepository.TODAY
    private val inner = FakeNutritionRepository()
    private var rearms = 0
    private val repo = RearmingNutritionRepository(inner, ReminderRearmer { rearms++ })
    private val oats = Meal(1, "Oats", Macros(35.0), emptyList(), null, null)

    @Before
    fun seed() {
        inner.meals.value = listOf(oats)
        inner.plans.value = listOf(
            MealPlan(10, "Cut", true, MacroTargets(), listOf(PlanSlot(21, "Breakfast", 1, "Oats", oats.macros, 450, 15))),
        )
        inner.supplements.value = listOf(SupplementStatus(5, "Creatine", "5 g", false, 0))
    }

    @Test
    fun `every write that changes what should fire requests a re-arm`() = runTest {
        val steps: List<suspend () -> Unit> = listOf(
            { repo.logSlot(today, 21, 1.0) },
            { repo.removeLog(inner.logs.value.getValue(today).single().id) },
            { repo.setSupplementTaken(today, 5, true) },
            { repo.saveSupplement(SupplementDraft(5, "Creatine", "5 g", reminderTimeMin = 480)) },
            { repo.savePlan(PlanDraft(10, "Cut", MacroTargets(), emptyList())) },
            { repo.setActivePlan(null) },
            { repo.deletePlan(10) },
            { repo.deleteSupplement(5) },
        )
        steps.forEachIndexed { index, step ->
            step()
            assertEquals("after step $index", index + 1, rearms)
        }
    }

    @Test
    fun `reads and unrelated writes do not`() = runTest {
        repo.saveMeal(MealDraft(null, "Toast", Macros(5.0), emptyList(), null, null))
        repo.logOffPlan(today, 1, 1.0)
        repo.updatePortion(inner.logs.value.getValue(today).single().id, 2.0)
        repo.addWater(today, 250)
        repo.duplicatePlan(10) { "$it (copy)" }
        repo.restoreMeal(1)
        repo.observeDay(today).first()
        repo.reminderSnapshot(today, today)
        assertEquals(0, rearms)
    }

    @Test
    fun `a failed write does not request a re-arm`() = runTest {
        inner.failNext = IllegalStateException("disk full")
        runCatching { repo.logSlot(today, 21, 1.0) }
        assertEquals(0, rearms)
    }

    @Test
    fun `a caller cancelled mid-write still requests a re-arm and stays cancelled`() = runTest {
        // The write may already have committed when cancellation lands (withTransaction resumes
        // into a cancelled caller), so the decorator re-arms on CancellationException too.
        inner.gate = CompletableDeferred()
        val job = launch { repo.logSlot(today, 21, 1.0) }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, rearms)
    }

    @Test
    fun `a throwing rearmer never fails the write`() = runTest {
        val throwing = RearmingNutritionRepository(inner, ReminderRearmer { error("scheduler exploded") })
        val award = throwing.logSlot(today, 21, 1.0)
        assertEquals(NutritionRules.MEAL_XP, award.xpDelta)
    }

    @Test
    fun `savePlan passes the plan id and the target re-evaluation award through`() = runTest {
        repo.logSlot(today, 21, 1.0) // 35 g protein, no target yet
        val result = repo.savePlan(PlanDraft(10, "Cut", MacroTargets(proteinG = 30.0), emptyList()))
        assertEquals(10L, result.planId)
        assertEquals(NutritionRules.PROTEIN_BONUS_XP, result.award.xpDelta)
    }
}
