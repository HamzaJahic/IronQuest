package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodSearchUseCase
import com.ironquest.app.domain.food.FoodSource
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.repository.FoodRepository
import com.ironquest.app.ui.food.builder.OnlineState
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.failedText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface CalculatorSearch {
    data object Idle : CalculatorSearch

    /** Your foods and the built-in list; [online] never hides them. */
    data class Local(val foods: List<FoodChoice>, val online: OnlineState = OnlineState.NotAsked) : CalculatorSearch
}

/** A food picked from the results, waiting for its amount. */
data class PendingIngredient(val food: FoodChoice, val unit: FoodUnit, val amountText: String) {
    val amount: Double? get() = FoodAmount.parse(amountText, unit)
}

data class IngredientCalculatorUiState(
    val query: String = "",
    val search: CalculatorSearch = CalculatorSearch.Idle,
    val pending: PendingIngredient? = null,
    val pendingInvalid: Boolean = false,
    val rows: List<IngredientRow> = emptyList(),
    /** Saving a picked online product failed; cleared by the next pick or query. */
    val pickError: UiText? = null,
) {
    val total: Macros? get() = IngredientCalculator.totalOf(rows)
    val canApply: Boolean get() = total != null
}

/**
 * State for the "Estimate from ingredients" sheet. Obtained with hiltViewModel() inside the sheet, so it is
 * scoped to the meal-editor back-stack entry: the list survives closing/reopening the sheet. The only write is
 * saving a picked online product into your foods, guarded by [writeMutex].
 */
@HiltViewModel
class IngredientCalculatorViewModel @Inject constructor(
    private val search: FoodSearchUseCase,
    private val foods: FoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IngredientCalculatorUiState())
    val uiState: StateFlow<IngredientCalculatorUiState> = _uiState.asStateFlow()

    private val writeMutex = Mutex()
    private var searchJob: Job? = null
    private var onlineJob: Job? = null
    private var lastOnline: List<OnlineFood> = emptyList()
    private var nextRowKey = 0L

    fun onQueryChange(text: String) {
        searchJob?.cancel()
        onlineJob?.cancel()
        lastOnline = emptyList()
        _uiState.update { it.copy(query = text, pickError = null, search = if (text.isBlank()) CalculatorSearch.Idle else it.search) }
        if (text.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val results = search.local(text).foods
            _uiState.update { state -> if (state.query == text) state.copy(search = CalculatorSearch.Local(results)) else state }
        }
        onlineJob = viewModelScope.launch {
            delay(ONLINE_DEBOUNCE_MS)
            val local = _uiState.value.search as? CalculatorSearch.Local ?: return@launch
            if (text.trim().length >= ONLINE_MIN_CHARS && local.foods.size < ONLINE_LOCAL_ENOUGH) runOnline(text)
        }
    }

    /** "Search online" and "Retry". */
    fun searchOnline() {
        val text = _uiState.value.query
        if (text.isBlank() || _uiState.value.search !is CalculatorSearch.Local) return
        onlineJob?.cancel()
        onlineJob = viewModelScope.launch { runOnline(text) }
    }

    private suspend fun runOnline(text: String) {
        setOnline(text, OnlineState.Loading)
        search.online(text.trim()).fold(
            onSuccess = { products ->
                lastOnline = products
                setOnline(text, OnlineState.Results(products.map(OnlineFood::toChoice)))
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                setOnline(text, OnlineState.Failed((error as? FoodSearchException)?.error ?: FoodSearchError.OFFLINE))
            },
        )
    }

    private fun setOnline(text: String, online: OnlineState) = _uiState.update { state ->
        val current = state.search
        if (state.query == text && current is CalculatorSearch.Local) state.copy(search = current.copy(online = online)) else state
    }

    fun pick(food: FoodChoice) {
        if (food.source == FoodSource.ONLINE) pickOnline(food) else openPending(food)
    }

    private fun pickOnline(choice: FoodChoice) {
        val product = lastOnline.firstOrNull { it.toChoice().key == choice.key } ?: return
        viewModelScope.launch {
            try {
                val saved = writeMutex.withLock { foods.saveOnlineFood(product) }
                openPending(saved.toChoice())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(pickError = failedText(R.string.food_custom_food_save_failed, error)) }
            }
        }
    }

    private fun openPending(food: FoodChoice) {
        val unit = FoodAmount.startUnit(food)
        _uiState.update { it.copy(pending = PendingIngredient(food, unit, FoodAmount.defaultText(unit)), pendingInvalid = false, pickError = null) }
    }

    fun onPendingAmountChange(text: String) = _uiState.update { state ->
        state.copy(pending = state.pending?.copy(amountText = text), pendingInvalid = false)
    }

    fun onPendingUnitChange(unit: FoodUnit) = _uiState.update { state ->
        state.copy(pending = state.pending?.copy(unit = unit, amountText = FoodAmount.defaultText(unit)), pendingInvalid = false)
    }

    fun cancelPending() = _uiState.update { it.copy(pending = null, pendingInvalid = false) }

    fun confirmPending() {
        val pending = _uiState.value.pending ?: return
        if (pending.amount == null) {
            _uiState.update { it.copy(pendingInvalid = true) }
            return
        }
        val row = IngredientRow(nextRowKey++, pending.food, pending.unit, pending.amountText.trim())
        _uiState.update { it.copy(pending = null, pendingInvalid = false, rows = it.rows + row) }
    }

    fun onRowAmountChange(key: Long, text: String) = _uiState.update { state ->
        state.copy(rows = state.rows.map { if (it.key == key) it.copy(amountText = text) else it })
    }

    fun removeRow(key: Long) = _uiState.update { state -> state.copy(rows = state.rows.filterNot { it.key == key }) }

    fun clearRows() = _uiState.update { it.copy(rows = emptyList()) }

    companion object {
        const val DEBOUNCE_MS = 250L
        const val ONLINE_DEBOUNCE_MS = 400L
        const val ONLINE_MIN_CHARS = 3
        const val ONLINE_LOCAL_ENOUGH = 5
    }
}
