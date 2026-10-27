package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class MealDetailViewModelTest {

    private val today = FakeNutritionRepository.TODAY
    private val clock = Clock.fixed(today.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repo = FakeNutritionRepository()

    @Test
    fun `a second tap while logging is in flight is ignored`() {
        repo.meals.value = listOf(Meal(1, "Oats", Macros(35.0), emptyList(), null, null))
        val gate = CompletableDeferred<Unit>()
        repo.gate = gate
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest {
                val vm = MealDetailViewModel(SavedStateHandle(mapOf(MealDetailViewModel.ARG_MEAL_ID to "1")), repo, clock)
                val events = mutableListOf<FoodEvent>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(events) }
                vm.logToday()
                vm.logToday()
                assertTrue(vm.uiState.value.isLogging)
                gate.complete(Unit)
                advanceUntilIdle()
                assertFalse(vm.uiState.value.isLogging)
                assertEquals(1, repo.logs.value[today]?.size)
                assertEquals(listOf<FoodEvent>(FoodEvent.Message(uiText(R.string.food_meal_logged)), FoodEvent.XpChanged(5)), events)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
