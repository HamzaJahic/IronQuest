package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.DayOverview
import com.ironquest.app.domain.nutrition.DaySummaryCalculator
import com.ironquest.app.domain.nutrition.MacroKind
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.fakes.FakeNutritionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FoodWeekViewModelTest {

    // Thursday 8 Oct 2026; its week is Mon 5 – Sun 11 Oct.
    private val today = LocalDate.of(2026, 10, 8)
    private val clock = MutableClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeNutritionRepository()

    private val oats = Macros(35.0, 75.0, 9.0, 9.0)
    private val shake = Macros(50.0, 10.0, 5.0, 0.0)
    private var nextLogId = 1L

    private fun plan(targets: MacroTargets) = MealPlan(10, "Cut", isActive = true, targets = targets, slots = emptyList())
    private fun log(macros: Macros, portion: Double = 1.0) = MealLogEntry(nextLogId++, null, null, "meal", portion, macros)

    private fun runVmTest(block: suspend TestScope.(FoodWeekViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = FoodWeekViewModel(repo, clock)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
                block(vm)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun FoodWeekViewModel.marks() = uiState.value.chart.bars.map { it.mark }

    @Test
    fun `opens on the current Monday-to-Sunday week and cannot go forward`() {
        runVmTest { vm ->
            val state = vm.uiState.value
            assertEquals(LocalDate.of(2026, 10, 5), state.weekStart)
            assertEquals(listOf(false, false, false, false, true, true, true), state.chart.bars.map { it.isFuture })
            assertFalse(state.canGoForward)
            vm.nextWeek()
            assertEquals(LocalDate.of(2026, 10, 5), vm.uiState.value.weekStart)
        }
    }

    @Test
    fun `bars follow the selected macro and its target line`() {
        repo.plans.value = listOf(plan(MacroTargets(proteinG = 100.0, carbsG = 200.0)))
        repo.logs.value = mapOf(
            LocalDate.of(2026, 10, 5) to listOf(log(oats)), // P35 → under
            LocalDate.of(2026, 10, 6) to listOf(log(shake, 3.0)), // P150 → over
            LocalDate.of(2026, 10, 7) to listOf(log(shake, 2.0)), // P100 → on target
        )
        runVmTest { vm ->
            val none = DayMark.NO_DATA
            assertEquals(listOf(DayMark.UNDER, DayMark.OVER, DayMark.ON_TARGET, none, none, none, none), vm.marks())
            assertEquals(100.0, checkNotNull(vm.uiState.value.chart.referenceTarget), 1e-9)

            vm.selectMacro(MacroKind.CARBS)
            assertEquals(listOf(DayMark.UNDER, DayMark.UNDER, DayMark.UNDER, none, none, none, none), vm.marks())
            assertEquals(200.0, checkNotNull(vm.uiState.value.chart.referenceTarget), 1e-9)

            vm.selectMacro(MacroKind.FAT)
            assertEquals(DayMark.NO_TARGET, vm.marks()[0])
            assertNull(vm.uiState.value.chart.referenceTarget)
        }
    }

    @Test
    fun `previous week shows older data and next week returns`() {
        repo.plans.value = listOf(plan(MacroTargets(proteinG = 100.0)))
        repo.logs.value = mapOf(LocalDate.of(2026, 9, 30) to listOf(log(shake, 2.0)))
        runVmTest { vm ->
            vm.previousWeek()
            val state = vm.uiState.value
            assertEquals(LocalDate.of(2026, 9, 28), state.weekStart)
            assertTrue(state.canGoForward)
            assertTrue(state.chart.bars.none { it.isFuture })
            assertEquals(DayMark.ON_TARGET, state.chart.bars[2].mark)
            assertEquals(1, checkNotNull(state.stats).loggedDays)
            assertEquals(1, checkNotNull(state.stats).onTargetDays)

            vm.nextWeek()
            assertEquals(LocalDate.of(2026, 10, 5), vm.uiState.value.weekStart)
        }
    }

    @Test
    fun `chart scale fits the larger of target and intake`() {
        val summary = DaySummaryCalculator.summarize(Macros(proteinG = 150.0, kcal = 600.0), MacroTargets(proteinG = 100.0))
        val chart = FoodWeekViewModel.chart(listOf(DayOverview(today, false, 1, summary, 0)), MacroKind.PROTEIN)
        assertEquals(150.0 / (150.0 * 1.15), chart.bars.single().fraction.toDouble(), 1e-6)
        assertEquals(100.0 / (150.0 * 1.15), checkNotNull(chart.targetFraction).toDouble(), 1e-6)

        val empty = FoodWeekViewModel.chart(listOf(DayOverview(today, false, 0, null, 0)), MacroKind.PROTEIN)
        assertEquals(0f, empty.bars.single().fraction, 0f)
        assertNull(empty.targetFraction)
    }

    @Test
    fun `refreshToday rolls into the new week only while following the current one`() {
        runVmTest { vm ->
            clock.instant = LocalDate.of(2026, 10, 12).atTime(9, 0).toInstant(ZoneOffset.UTC)
            vm.refreshToday()
            assertEquals(LocalDate.of(2026, 10, 12), vm.uiState.value.weekStart)

            vm.previousWeek()
            clock.instant = LocalDate.of(2026, 10, 19).atTime(9, 0).toInstant(ZoneOffset.UTC)
            vm.refreshToday()
            assertEquals(LocalDate.of(2026, 10, 5), vm.uiState.value.weekStart)
        }
    }

    @Test
    fun `empty current week still draws the live plan target`() {
        repo.plans.value = listOf(plan(MacroTargets(proteinG = 160.0)))
        runVmTest { vm ->
            val chart = vm.uiState.value.chart
            assertEquals(160.0, checkNotNull(chart.referenceTarget), 1e-9)
            assertTrue(chart.targetFraction != null)
        }
    }

    @Test
    fun `live target beats a stale snapshot in the current week`() {
        repo.plans.value = listOf(plan(MacroTargets(proteinG = 180.0)))
        repo.daySnapshots.value = mapOf(LocalDate.of(2026, 10, 5) to MacroTargets(proteinG = 150.0))
        repo.logs.value = mapOf(LocalDate.of(2026, 10, 5) to listOf(log(oats)))
        runVmTest { vm ->
            assertEquals(180.0, checkNotNull(vm.uiState.value.chart.referenceTarget), 1e-9)
        }
    }
}
