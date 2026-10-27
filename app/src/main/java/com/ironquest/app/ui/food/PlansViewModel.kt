package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.importing.CsvExport
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.ui.text.failedText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class PlansUiState(val isLoading: Boolean = true, val plans: List<MealPlan> = emptyList())

@HiltViewModel
class PlansViewModel @Inject constructor(private val repository: NutritionRepository) : ViewModel() {

    val uiState: StateFlow<PlansUiState> = repository.observePlans()
        .map { PlansUiState(isLoading = false, plans = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    private val _events = Channel<FoodEvent>(Channel.BUFFERED)
    val events: Flow<FoodEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    /** Only one plan can be active: switching one on switches the others off. */
    fun setActive(planId: Long, active: Boolean) {
        if (!active && uiState.value.plans.none { it.id == planId && it.isActive }) return
        write { repository.setActivePlan(if (active) planId else null) }
    }

    /** [copyName] builds the copy's name from the original's (translated wording, so the screen supplies it). */
    fun duplicate(planId: Long, copyName: (String) -> String) = write {
        repository.duplicatePlan(planId, copyName)
        NutritionAward.NONE
    }

    fun delete(planId: Long) = write { repository.deletePlan(planId) }

    /** The plan as import-ready CSV (with each meal's tags and recipe), or null if it no longer exists. */
    suspend fun exportCsv(planId: Long): String? {
        val plan = repository.observePlan(planId).first() ?: return null
        val meals = plan.slots.map { it.mealId }.distinct().associateWith { repository.observeMeal(it).first() }
        return CsvExport.mealPlan(plan.slots.map { it to meals[it.mealId] })
    }

    private fun write(block: suspend () -> NutritionAward) {
        viewModelScope.launch {
            try {
                _events.sendAward(writeMutex.withLock { block() })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(FoodEvent.Message(failedText(R.string.food_plan_update_failed, error)))
            }
        }
    }
}
