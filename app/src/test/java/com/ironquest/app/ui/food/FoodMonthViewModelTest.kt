package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.SupplementStatus
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class FoodMonthViewModelTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val clock = MutableClock(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeNutritionRepository()

    private val oats = Macros(35.0, 75.0, 9.0, 9.0)
    private val shake = Macros(50.0, 10.0, 5.0, 0.0)
    private var nextLogId = 1L

    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)
    private fun proteinPlan() = MealPlan(10, "Cut", isActive = true, targets = MacroTargets(proteinG = 100.0), slots = emptyList())
    private fun log(macros: Macros, portion: Double = 1.0) = MealLogEntry(nextLogId++, null, null, "meal", portion, macros)

    private fun runVmTest(block: suspend TestScope.(FoodMonthViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = FoodMonthViewModel(repo, clock)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
                block(vm)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `month grid is coloured by protein status and future days have no data`() {
        repo.plans.value = listOf(proteinPlan())
        repo.logs.value = mapOf(
            oct(1) to listOf(log(shake, 2.0)),
            oct(2) to listOf(log(oats)),
            oct(3) to listOf(log(shake, 3.0)),
        )
        runVmTest { vm ->
            val state = vm.uiState.value
            assertEquals(YearMonth.of(2026, 10), state.month)
            assertEquals(31, state.marks.size)
            assertEquals(DayMark.ON_TARGET, state.marks[oct(1)])
            assertEquals(DayMark.UNDER, state.marks[oct(2)])
            assertEquals(DayMark.OVER, state.marks[oct(3)])
            assertEquals(DayMark.NO_DATA, state.marks[oct(4)])
            assertEquals(DayMark.NO_DATA, state.marks[oct(20)])
            assertFalse(state.showsNoTarget)
        }
    }

    @Test
    fun `cannot go past the current month and the previous month reloads`() {
        repo.plans.value = listOf(proteinPlan())
        repo.logs.value = mapOf(LocalDate.of(2026, 9, 15) to listOf(log(shake, 2.0)))
        runVmTest { vm ->
            assertFalse(vm.uiState.value.canGoForward)
            vm.nextMonth()
            assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)

            vm.previousMonth()
            val state = vm.uiState.value
            assertEquals(YearMonth.of(2026, 9), state.month)
            assertEquals(30, state.marks.size)
            assertTrue(state.canGoForward)
            assertEquals(DayMark.ON_TARGET, state.marks[LocalDate.of(2026, 9, 15)])
        }
    }

    @Test
    fun `summary counts on-target logged days and the streak at the last visible day`() {
        repo.plans.value = listOf(proteinPlan())
        repo.logs.value = mapOf(oct(1) to listOf(log(shake, 2.0)), oct(2) to listOf(log(oats)))
        repo.supplements.value = listOf(SupplementStatus(7, "Creatine", "5 g", taken = false, streak = 0))
        repo.intakes.value = setOf(
            7L to LocalDate.of(2026, 9, 29),
            7L to LocalDate.of(2026, 9, 30),
            7L to oct(7),
            7L to oct(8),
        )
        runVmTest { vm ->
            var stats = checkNotNull(vm.uiState.value.stats)
            assertEquals(2, stats.loggedDays)
            assertEquals(1, stats.onTargetDays)
            val october = stats.supplements.single()
            assertEquals(2, october.streak) // 8, 7 Oct
            assertEquals(2, october.takenDays)
            assertEquals(8, october.ofDays) // 1..8 Oct elapsed

            vm.previousMonth()
            stats = checkNotNull(vm.uiState.value.stats)
            assertEquals(2, stats.supplements.single().streak) // anchored at 30 Sep: 30, 29
            assertEquals(30, stats.supplements.single().ofDays)
            assertEquals(0, stats.loggedDays)
        }
    }

    @Test
    fun `refreshToday rolls into the new month only while following the current one`() {
        runVmTest { vm ->
            clock.instant = LocalDate.of(2026, 10, 31).atTime(9, 0).toInstant(ZoneOffset.UTC)
            vm.refreshToday()
            clock.instant = LocalDate.of(2026, 11, 1).atTime(9, 0).toInstant(ZoneOffset.UTC)
            vm.refreshToday()
            assertEquals(YearMonth.of(2026, 11), vm.uiState.value.month)

            vm.previousMonth()
            clock.instant = LocalDate.of(2026, 12, 1).atTime(9, 0).toInstant(ZoneOffset.UTC)
            vm.refreshToday()
            assertEquals(YearMonth.of(2026, 10), vm.uiState.value.month)
        }
    }
}
