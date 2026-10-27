package com.ironquest.app.ui.food

import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.fakes.FakeAppSettingsRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FoodDayViewModelTest {

    private val today = FakeNutritionRepository.TODAY
    private val clock = MutableClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeNutritionRepository()
    private val settings = FakeAppSettingsRepository()

    private val oats = Meal(1, "Oats", Macros(35.0, 75.0, 9.0, 9.0), listOf("breakfast"), null, null)
    private val chicken = Meal(2, "Chicken rice", Macros(45.0, 60.0, 12.0, 3.0), emptyList(), null, null)

    private fun activePlan(proteinTarget: Double? = 180.0) = MealPlan(
        id = 10,
        name = "Cut",
        isActive = true,
        targets = MacroTargets(proteinG = proteinTarget),
        slots = listOf(
            PlanSlot(21, "Breakfast", 1, "Oats", oats.macros, 450, null),
            PlanSlot(22, "Lunch", 2, "Chicken rice", chicken.macros, 780, null),
        ),
    )

    private fun runVmTest(block: suspend TestScope.(FoodDayViewModel, MutableList<FoodEvent>) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = FoodDayViewModel(repo, settings, clock)
                val events = mutableListOf<FoodEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm, events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun FoodDayViewModel.slot(id: Long) = uiState.value.slots.first { it.slot.id == id }

    @Test
    fun `checking a slot logs it and pays meal XP`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            assertTrue(vm.slot(21).checked)
            assertEquals(35.0, checkNotNull(vm.uiState.value.summary).protein.eaten, 1e-9)
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(5)), events)
        }
    }

    @Test
    fun `unchecking a slot removes the log and takes the XP back`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            vm.onAction(FoodDayAction.ToggleSlot(21))
            assertFalse(vm.slot(21).checked)
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(5), FoodEvent.XpChanged(-5)), events)
        }
    }

    @Test
    fun `hitting the protein target adds the bonus to the same award`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan(proteinTarget = 80.0))
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            vm.onAction(FoodDayAction.ToggleSlot(22))
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(5), FoodEvent.XpChanged(30)), events)
        }
    }

    @Test
    fun `a second tap while the first write is in flight is ignored`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            vm.onAction(FoodDayAction.ToggleSlot(21))
            assertTrue(vm.slot(21).isSaving)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repo.logSlotCalls)
            assertFalse(vm.slot(21).isSaving)
            assertTrue(vm.slot(21).checked)
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(5)), events)
        }
    }

    @Test
    fun `cannot navigate into the future`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.NextDay)
            assertEquals(today, vm.uiState.value.date)
            assertFalse(vm.uiState.value.canGoForward)
            vm.onAction(FoodDayAction.PreviousDay)
            assertEquals(today.minusDays(1), vm.uiState.value.date)
            assertTrue(vm.uiState.value.canGoForward)
        }
    }

    @Test
    fun `without an active plan off-plan logging still works`() {
        repo.meals.value = listOf(oats)
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.LogOffPlan(1))
            assertNull(vm.uiState.value.planName)
            assertEquals(1, vm.uiState.value.offPlan.size)
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(5)), events)
        }
    }

    @Test
    fun `slot logs from another plan move to earlier`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            repo.plans.value = listOf(activePlan().copy(id = 11, slots = listOf(PlanSlot(31, "Lunch", 2, "Chicken rice", chicken.macros, null, null))))
            assertEquals(listOf(21L), vm.uiState.value.earlier.map { it.slotId })
            assertFalse(vm.slot(31).checked)
        }
    }

    @Test
    fun `out of range portion is rejected with a message`() {
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.SetPortion(1, "9"))
            assertEquals(FoodEvent.Message(uiText(R.string.food_day_portion_range)), events.single())
        }
    }

    @Test
    fun `losing the protein target on uncheck revokes the bonus`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan(proteinTarget = 80.0))
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            vm.onAction(FoodDayAction.ToggleSlot(22))
            vm.onAction(FoodDayAction.ToggleSlot(22))
            assertEquals(
                listOf<FoodEvent>(FoodEvent.XpChanged(5), FoodEvent.XpChanged(30), FoodEvent.XpChanged(-30)),
                events,
            )
        }
    }

    @Test
    fun `water bonus pays once at the target and is revoked below it`() {
        repo.plans.value = listOf(activePlan().copy(targets = MacroTargets(waterMl = 500)))
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.AddWater)
            assertTrue(events.isEmpty())
            vm.onAction(FoodDayAction.AddWater)
            vm.onAction(FoodDayAction.RemoveWater)
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(10), FoodEvent.XpChanged(-10)), events)
        }
    }

    @Test
    fun `a valid portion updates the log`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.ToggleSlot(21))
            vm.onAction(FoodDayAction.SetPortion(checkNotNull(vm.slot(21).log).id, "1,5"))
            assertEquals(1.5, checkNotNull(vm.slot(21).log).portion, 1e-9)
        }
    }

    @Test
    fun `supplement check pays 2 XP and uncheck takes it back`() {
        repo.supplements.value = listOf(SupplementStatus(5, "Creatine", "5 g", false, 0))
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.ToggleSupplement(5))
            assertTrue(vm.uiState.value.supplements.single().taken)
            vm.onAction(FoodDayAction.ToggleSupplement(5))
            assertEquals(listOf<FoodEvent>(FoodEvent.XpChanged(2), FoodEvent.XpChanged(-2)), events)
        }
    }

    @Test
    fun `a repository error emits a message and clears the saving flag`() {
        repo.meals.value = listOf(oats, chicken)
        repo.plans.value = listOf(activePlan())
        runVmTest { vm, events ->
            repo.failNext = IllegalStateException("disk full")
            vm.onAction(FoodDayAction.ToggleSlot(21))
            assertEquals(FoodEvent.Message(uiText(R.string.food_day_save_failed, UiText.Verbatim("disk full"))), events.single())
            assertFalse(vm.slot(21).isSaving)
            assertFalse(vm.slot(21).checked)
        }
    }

    @Test
    fun `refreshToday follows midnight when the user is on today`() {
        runVmTest { vm, _ ->
            clock.instant = clock.instant.plus(1, ChronoUnit.DAYS)
            vm.onAction(FoodDayAction.RefreshToday)
            assertEquals(today.plusDays(1), vm.uiState.value.date)
            assertTrue(vm.uiState.value.isToday)
        }
    }

    @Test
    fun `refreshToday leaves a day the user navigated to alone`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.PreviousDay)
            clock.instant = clock.instant.plus(1, ChronoUnit.DAYS)
            vm.onAction(FoodDayAction.RefreshToday)
            assertEquals(today.minusDays(1), vm.uiState.value.date)
        }
    }

    @Test
    fun `navigating back to today resumes following it`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.PreviousDay)
            vm.onAction(FoodDayAction.NextDay)
            clock.instant = clock.instant.plus(1, ChronoUnit.DAYS)
            vm.onAction(FoodDayAction.RefreshToday)
            assertEquals(today.plusDays(1), vm.uiState.value.date)
        }
    }

    @Test
    fun `writes use the date of the visible state`() {
        repo.meals.value = listOf(oats)
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.PreviousDay)
            vm.onAction(FoodDayAction.LogOffPlan(1))
            assertEquals(1, repo.logs.value[today.minusDays(1)]?.size)
        }
    }

    @Test
    fun `showDate opens a past day and ignores the future`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.ShowDate(today.minusDays(3)))
            assertEquals(today.minusDays(3), vm.uiState.value.date)
            vm.onAction(FoodDayAction.ShowDate(today.plusDays(1)))
            assertEquals(today.minusDays(3), vm.uiState.value.date)
            clock.instant = clock.instant.plus(1, ChronoUnit.DAYS)
            vm.onAction(FoodDayAction.RefreshToday)
            assertEquals(today.minusDays(3), vm.uiState.value.date) // a picked day is not followed
        }
    }

    @Test
    fun `saving a supplement stores its reminder time`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.SaveSupplement(null, "Fish oil", "2 caps", 480))
            assertEquals(480, repo.supplements.value.single().reminderTimeMin)
            assertEquals(480, vm.uiState.value.supplements.single().reminderTimeMin)
        }
    }

    @Test
    fun `an out-of-range reminder time is rejected with a message`() {
        runVmTest { vm, events ->
            vm.onAction(FoodDayAction.SaveSupplement(null, "Fish oil", "", 1440))
            assertTrue(repo.supplements.value.isEmpty())
            assertEquals(FoodEvent.Message(uiText(R.string.food_day_reminder_invalid)), events.single())
        }
    }

    @Test
    fun `editing a supplement keeps its reminder when the current value is passed through`() {
        runVmTest { vm, _ ->
            vm.onAction(FoodDayAction.SaveSupplement(null, "Fish oil", "2 caps", 480))
            val id = repo.supplements.value.single().id
            vm.onAction(FoodDayAction.SaveSupplement(id, "Fish oil XL", "3 caps", vm.uiState.value.supplements.single().reminderTimeMin))
            val saved = repo.supplements.value.single()
            assertEquals("Fish oil XL", saved.name)
            assertEquals(480, saved.reminderTimeMin)
        }
    }

    private fun quickLog(id: Long, slotId: Long? = null) = MealLogEntry(
        id, slotId, null, "Lunch", 1.0, Macros(10.0, 20.0, 5.0, 1.0),
        items = listOf(MealLogItem("rice", "Rice", 100.0, FoodUnit(UnitKind.GRAM, 1.0), Macros(10.0, 20.0, 5.0, 1.0))),
    )

    @Test
    fun `a quick meal goes to quickMeals, not offPlan`() {
        val plain = MealLogEntry(2, null, 1, "Oats", 1.0, oats.macros)
        val day = DayNutrition(today, null, null, listOf(quickLog(1), plain), 0, emptyList())
        val state = FoodDayViewModel.buildState(day, emptyList(), today, emptySet())
        assertEquals(listOf(1L), state.quickMeals.map { it.id })
        assertEquals(listOf(2L), state.offPlan.map { it.id })
    }

    @Test
    fun `the tracking mode is passed through`() {
        val day = DayNutrition(today, null, null, emptyList(), 0, emptyList())
        val state = FoodDayViewModel.buildState(day, emptyList(), today, emptySet(), FoodTrackingMode.ON_THE_FLY)
        assertEquals(FoodTrackingMode.ON_THE_FLY, state.mode)
    }

    @Test
    fun `on the fly shows no slots even with an active plan`() {
        val day = DayNutrition(today, activePlan(), null, emptyList(), 0, emptyList())
        val state = FoodDayViewModel.buildState(day, emptyList(), today, emptySet(), FoodTrackingMode.ON_THE_FLY)
        assertTrue(state.slots.isEmpty())
        assertEquals(2, FoodDayViewModel.buildState(day, emptyList(), today, emptySet(), FoodTrackingMode.PLAN).slots.size)
    }

    @Test
    fun `on the fly lists planned logs as plain logs`() {
        val slotId = checkNotNull(activePlan().slots.firstOrNull()).id
        val planned = MealLogEntry(3, slotId, 1, "Oats", 1.0, oats.macros)
        val day = DayNutrition(today, activePlan(), null, listOf(planned, quickLog(1)), 0, emptyList())
        val state = FoodDayViewModel.buildState(day, emptyList(), today, emptySet(), FoodTrackingMode.ON_THE_FLY)
        assertEquals(listOf(3L), state.offPlan.map { it.id })
        assertEquals(listOf(1L), state.quickMeals.map { it.id })
    }

    @Test
    fun `plan mode keeps quick meals from earlier`() {
        val day = DayNutrition(today, activePlan(), null, listOf(quickLog(1)), 0, emptyList())
        val state = FoodDayViewModel.buildState(day, emptyList(), today, emptySet(), FoodTrackingMode.PLAN)
        assertEquals(listOf(1L), state.quickMeals.map { it.id })
    }

    @Test
    fun `SetOwnTargets writes to the settings`() = runVmTest { vm, _ ->
        val targets = MacroTargets(proteinG = 140.0, kcal = 2200.0)
        vm.onAction(FoodDayAction.SetOwnTargets(targets))
        assertEquals(targets, settings.food.value.ownTargets)
    }
}

class MutableClock(var instant: Instant, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
}
