package com.ironquest.app.ui.food

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealUsage
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.ui.text.failedText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class MealDetailUiState(
    val isLoading: Boolean = true,
    val meal: Meal? = null,
    val usages: List<MealUsage> = emptyList(),
    val isLogging: Boolean = false,
)

@HiltViewModel
class MealDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val mealId: Long = checkNotNull(savedStateHandle.get<String>(ARG_MEAL_ID)?.toLongOrNull()) { "mealId missing" }

    private val logging = MutableStateFlow(false)

    val uiState: StateFlow<MealDetailUiState> =
        combine(repository.observeMeal(mealId), repository.observeMealUsages(mealId), logging) { meal, usages, isLogging ->
            MealDetailUiState(isLoading = false, meal = meal, usages = usages, isLogging = isLogging)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealDetailUiState())

    private val _events = Channel<FoodEvent>(Channel.BUFFERED)
    val events: Flow<FoodEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    fun logToday() {
        if (logging.value) return
        logging.value = true
        viewModelScope.launch {
            try {
                val award = writeMutex.withLock { repository.logOffPlan(LocalDate.now(clock), mealId, 1.0) }
                _events.send(FoodEvent.Message(uiText(R.string.food_meal_logged)))
                _events.sendAward(award)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(FoodEvent.Message(failedText(R.string.food_meal_log_failed, error)))
            } finally {
                logging.value = false
            }
        }
    }

    /** Un-archives this meal so it shows in the library and pickers again. */
    fun restore() {
        viewModelScope.launch {
            try {
                writeMutex.withLock { repository.restoreMeal(mealId) }
                _events.send(FoodEvent.Message(uiText(R.string.food_meal_restored)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(FoodEvent.Message(failedText(R.string.food_meal_restore_failed, error)))
            }
        }
    }

    companion object {
        const val ARG_MEAL_ID = "mealId" // not UI text: navigation argument key
    }
}
