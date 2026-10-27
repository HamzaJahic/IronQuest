package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.fakes.FakeNutritionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {

    private val repo = FakeNutritionRepository().apply {
        plans.value = listOf(
            MealPlan(1, "Cut", true, MacroTargets(), emptyList()),
            MealPlan(2, "Bulk", false, MacroTargets(), emptyList()),
        )
    }

    private val events = mutableListOf<FoodEvent>()

    private fun runVmTest(block: suspend TestScope.(PlansViewModel) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = PlansViewModel(repo)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                block(vm)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching a plan on activates only that plan`() = runVmTest { vm ->
        vm.setActive(2, true)
        assertEquals(listOf<Long?>(2), repo.activeCalls)
        assertEquals(listOf(2L), vm.uiState.value.plans.filter { it.isActive }.map { it.id })
    }

    @Test
    fun `switching the active plan off leaves none active`() = runVmTest { vm ->
        vm.setActive(1, false)
        assertEquals(listOf<Long?>(null), repo.activeCalls)
        assertEquals(emptyList<Long>(), vm.uiState.value.plans.filter { it.isActive }.map { it.id })
    }

    @Test
    fun `duplicate adds an inactive copy`() = runVmTest { vm ->
        vm.duplicate(1) { "$it (copy)" }
        val copy = vm.uiState.value.plans.single { it.name == "Cut (copy)" }
        assertEquals(false, copy.isActive)
    }

    @Test
    fun `switching off a plan that is not active does nothing`() = runVmTest { vm ->
        vm.setActive(2, false)
        assertEquals(emptyList<Long?>(), repo.activeCalls)
        assertEquals(listOf(1L), vm.uiState.value.plans.filter { it.isActive }.map { it.id })
    }

    @Test
    fun `deleting a plan removes it without an error event`() = runVmTest { vm ->
        vm.delete(2)
        assertEquals(listOf(1L), vm.uiState.value.plans.map { it.id })
        assertEquals(emptyList<FoodEvent>(), events)
    }
}
