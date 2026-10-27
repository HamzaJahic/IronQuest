package com.ironquest.app.ui.food.builder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.food.FoodAmount
import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodKey
import com.ironquest.app.domain.food.FoodSearchUseCase
import com.ironquest.app.domain.food.FoodSource
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.LocalFoodResults
import com.ironquest.app.domain.food.MealNameRules
import com.ironquest.app.domain.food.QuickMealName
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.repository.FoodRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.ui.food.IngredientCalculator
import com.ironquest.app.ui.food.formatAmount
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.failedText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class BuilderItem(val key: Long, val item: MealLogItem)

/** The food picked from results or recents, waiting for its amount. */
data class PendingFood(
    val choice: FoodChoice?,
    val snapshot: MealLogItem?,
    val unit: FoodUnit,
    val amountText: String,
    val editingKey: Long? = null,
) {
    /** Recents of a deleted food keep only the snapshot's unit. */
    val units: List<FoodUnit> get() = choice?.units ?: listOfNotNull(snapshot?.unit)
}

sealed interface BuilderSearch {
    data object Idle : BuilderSearch
    data class Local(val results: LocalFoodResults, val online: OnlineState = OnlineState.NotAsked) : BuilderSearch
}

/** Open Food Facts results next to the local ones; a failure never hides local results. */
sealed interface OnlineState {
    data object NotAsked : OnlineState
    data object Loading : OnlineState
    data class Results(val foods: List<FoodChoice>) : OnlineState
    data class Failed(val error: FoodSearchError) : OnlineState
}

data class MealBuilderUiState(
    val isLoading: Boolean = true,
    val date: LocalDate,
    val logId: Long? = null,
    val nameChoice: QuickMealName? = QuickMealName.SNACK,
    /** Used when [nameChoice] is null; also holds an edited meal's stored name. */
    val customName: String = "",
    val items: List<BuilderItem> = emptyList(),
    val query: String = "",
    val search: BuilderSearch = BuilderSearch.Idle,
    val recentMeals: List<MealLogEntry> = emptyList(),
    val recentFoods: List<MealLogItem> = emptyList(),
    val pending: PendingFood? = null,
    val pendingInvalid: Boolean = false,
    /** Library meal waiting for its portion (builder was empty). */
    val pendingMeal: Meal? = null,
    val isSaving: Boolean = false,
    val loadedItems: List<MealLogItem> = emptyList(),
    val loadedName: String? = null,
) {
    val total: Macros get() = items.fold(Macros.ZERO) { sum, row -> sum + row.item.macros }
    val canFinish: Boolean get() = items.isNotEmpty() && !isSaving
    val hasChanges: Boolean get() = items.map { it.item } != loadedItems
    val showsRecents: Boolean get() = query.isBlank() && pending == null

    /** [dialogOpen]: one of the screen's own dialogs (name, portion, new food, discard) is up. */
    fun onboardingFacts(dialogOpen: Boolean): Set<OnboardingFact> = buildSet {
        if (showsRecents && (recentMeals.isNotEmpty() || recentFoods.isNotEmpty())) add(OnboardingFact.RECENTS_VISIBLE)
        if (pending != null) add(OnboardingFact.AMOUNT_ROW_OPEN)
        if (query.isNotBlank() && search is BuilderSearch.Local) add(OnboardingFact.CREATE_FOOD_VISIBLE)
        if (dialogOpen || pendingMeal != null) add(OnboardingFact.BLOCKED)
    }
}

sealed interface MealBuilderAction {
    data class QueryChanged(val text: String) : MealBuilderAction
    data class PickFood(val choice: FoodChoice) : MealBuilderAction
    data object SearchOnline : MealBuilderAction
    data class PickMeal(val meal: Meal) : MealBuilderAction
    data class PickRecentFood(val item: MealLogItem) : MealBuilderAction
    data class PickRecentMeal(val log: MealLogEntry) : MealBuilderAction
    data class EditItem(val key: Long) : MealBuilderAction
    data class RemoveItem(val key: Long) : MealBuilderAction
    data class PendingAmountChanged(val text: String) : MealBuilderAction
    data class PendingUnitChanged(val unit: FoodUnit) : MealBuilderAction
    data object ConfirmPending : MealBuilderAction
    data object CancelPending : MealBuilderAction
    data class LogMealPortion(val portionText: String) : MealBuilderAction
    data object CancelMealPortion : MealBuilderAction
    data class ChooseName(val name: QuickMealName) : MealBuilderAction
    data class CustomName(val text: String) : MealBuilderAction
    data class CreateFood(val draft: UserFoodDraft) : MealBuilderAction

    /** [resolvedName] = the localized label of the chosen name, or the custom text (the screen resolves it). */
    data class Done(val resolvedName: String) : MealBuilderAction
    data class SaveAsMeal(val resolvedName: String, val recipeText: String) : MealBuilderAction
}

sealed interface MealBuilderEvent {
    data class Finished(val award: NutritionAward) : MealBuilderEvent
    data class Message(val text: UiText) : MealBuilderEvent
}

@HiltViewModel
class MealBuilderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val nutrition: NutritionRepository,
    private val foods: FoodRepository,
    private val search: FoodSearchUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle.numberArg(ARG_DATE) ?: LocalDate.now(clock).toEpochDay())
    private val logId: Long? = savedStateHandle.numberArg(ARG_LOG_ID)?.takeIf { it > 0 }

    private val _uiState = MutableStateFlow(
        MealBuilderUiState(
            isLoading = logId != null,
            date = date,
            logId = logId,
            nameChoice = if (logId == null) MealNameRules.forTime(minuteOfDay().takeIf { date == LocalDate.now(clock) }) else null,
        ),
    )
    val uiState: StateFlow<MealBuilderUiState> = _uiState.asStateFlow()

    private val _events = Channel<MealBuilderEvent>(Channel.BUFFERED)
    val events: Flow<MealBuilderEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()
    private var searchJob: Job? = null
    private var onlineJob: Job? = null
    private var lastOnline: List<OnlineFood> = emptyList()
    private var nextKey = 1L

    init {
        viewModelScope.launch { foods.observeRecentFoods().collect { recent -> _uiState.update { it.copy(recentFoods = recent) } } }
        viewModelScope.launch { foods.observeRecentMeals().collect { recent -> _uiState.update { it.copy(recentMeals = recent) } } }
        if (logId != null) viewModelScope.launch { load(logId) }
    }

    fun onAction(action: MealBuilderAction) {
        when (action) {
            is MealBuilderAction.QueryChanged -> onQueryChanged(action.text)
            is MealBuilderAction.PickFood ->
                if (action.choice.source == FoodSource.ONLINE) pickOnline(action.choice) else openPending(PendingFood(action.choice, snapshot = null, unit = FoodAmount.startUnit(action.choice), amountText = FoodAmount.defaultText(FoodAmount.startUnit(action.choice))))
            MealBuilderAction.SearchOnline -> searchOnlineNow()
            is MealBuilderAction.PickMeal -> pickMeal(action.meal)
            is MealBuilderAction.PickRecentFood -> pickRecentFood(action.item)
            is MealBuilderAction.PickRecentMeal -> addAll(action.log.items)
            is MealBuilderAction.EditItem -> editItem(action.key)
            is MealBuilderAction.RemoveItem -> _uiState.update { state -> state.copy(items = state.items.filterNot { it.key == action.key }) }
            is MealBuilderAction.PendingAmountChanged -> _uiState.update { state -> state.copy(pending = state.pending?.copy(amountText = action.text), pendingInvalid = false) }
            is MealBuilderAction.PendingUnitChanged -> _uiState.update { state ->
                state.copy(pending = state.pending?.copy(unit = action.unit, amountText = FoodAmount.defaultText(action.unit)), pendingInvalid = false)
            }
            MealBuilderAction.ConfirmPending -> confirmPending()
            MealBuilderAction.CancelPending -> _uiState.update { it.copy(pending = null, pendingInvalid = false) }
            is MealBuilderAction.LogMealPortion -> logMealPortion(action.portionText)
            MealBuilderAction.CancelMealPortion -> _uiState.update { it.copy(pendingMeal = null, pendingInvalid = false) }
            is MealBuilderAction.ChooseName -> _uiState.update { it.copy(nameChoice = action.name) }
            is MealBuilderAction.CustomName -> _uiState.update { it.copy(nameChoice = null, customName = action.text) }
            is MealBuilderAction.CreateFood -> createFood(action.draft)
            is MealBuilderAction.Done -> done(action.resolvedName)
            is MealBuilderAction.SaveAsMeal -> saveAsMeal(action.resolvedName, action.recipeText)
        }
    }

    private fun createFood(draft: UserFoodDraft) {
        viewModelScope.launch {
            try {
                val id = writeMutex.withLock { foods.saveUserFood(draft) }
                val query = _uiState.value.query
                if (query.isNotBlank()) {
                    val results = search.local(query)
                    _uiState.update { state -> if (state.query == query) state.copy(search = BuilderSearch.Local(results)) else state }
                }
                val choice = search.resolve(FoodKey.mine(id))
                if (choice != null) onAction(MealBuilderAction.PickFood(choice))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(MealBuilderEvent.Message(failedText(R.string.food_custom_food_save_failed, error)))
            }
        }
    }

    private fun minuteOfDay(): Int = LocalTime.now(clock).let { it.hour * 60 + it.minute }

    private suspend fun load(id: Long) {
        val log = nutrition.getLog(id)
        _uiState.update { state ->
            if (log == null) {
                state.copy(isLoading = false)
            } else {
                state.copy(
                    isLoading = false,
                    nameChoice = null,
                    customName = log.mealName,
                    items = log.items.map(::keyed),
                    loadedItems = log.items,
                    loadedName = log.mealName,
                )
            }
        }
    }

    private fun keyed(item: MealLogItem) = BuilderItem(nextKey++, item)

    private fun onQueryChanged(text: String) {
        searchJob?.cancel()
        onlineJob?.cancel()
        lastOnline = emptyList()
        _uiState.update { it.copy(query = text, search = if (text.isBlank()) BuilderSearch.Idle else it.search) }
        if (text.isBlank()) return
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val results = search.local(text)
            _uiState.update { state -> if (state.query == text) state.copy(search = BuilderSearch.Local(results)) else state }
        }
        onlineJob = viewModelScope.launch {
            delay(ONLINE_DEBOUNCE_MS)
            val local = _uiState.value.search as? BuilderSearch.Local ?: return@launch
            if (text.trim().length >= ONLINE_MIN_CHARS && local.results.count < ONLINE_LOCAL_ENOUGH) runOnline(text)
        }
    }

    private fun searchOnlineNow() {
        val text = _uiState.value.query
        if (text.isBlank() || _uiState.value.search !is BuilderSearch.Local) return
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
                val reason = (error as? FoodSearchException)?.error ?: FoodSearchError.OFFLINE
                setOnline(text, OnlineState.Failed(reason))
            },
        )
    }

    private fun setOnline(text: String, online: OnlineState) = _uiState.update { state ->
        val current = state.search
        if (state.query == text && current is BuilderSearch.Local) state.copy(search = current.copy(online = online)) else state
    }

    private fun pickOnline(choice: FoodChoice) {
        val food = lastOnline.firstOrNull { it.toChoice().key == choice.key } ?: return
        viewModelScope.launch {
            try {
                val saved = writeMutex.withLock { foods.saveOnlineFood(food) }
                onAction(MealBuilderAction.PickFood(saved.toChoice()))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(MealBuilderEvent.Message(failedText(R.string.food_custom_food_save_failed, error)))
            }
        }
    }

    private fun openPending(pending: PendingFood) = _uiState.update { it.copy(pending = pending, pendingInvalid = false) }

    private fun pickRecentFood(item: MealLogItem) {
        viewModelScope.launch {
            val choice = search.resolve(item.foodKey)
            openPending(PendingFood(choice, snapshot = item, unit = item.unit, amountText = item.amount.formatAmount()))
        }
    }

    private fun editItem(key: Long) {
        val item = _uiState.value.items.firstOrNull { it.key == key }?.item ?: return
        viewModelScope.launch {
            val choice = search.resolve(item.foodKey)
            openPending(PendingFood(choice, snapshot = item, unit = item.unit, amountText = item.amount.formatAmount(), editingKey = key))
        }
    }

    private fun addAll(items: List<MealLogItem>) = _uiState.update { state -> state.copy(items = state.items + items.map(::keyed)) }

    private fun pickMeal(meal: Meal) {
        if (_uiState.value.items.isEmpty()) {
            _uiState.update { it.copy(pendingMeal = meal, pendingInvalid = false) }
            return
        }
        val item = MealLogItem(FoodKey.meal(meal.id), meal.name, 1.0, FoodUnit.MEAL_PORTION, meal.macros * 1.0)
        val added = keyed(item)
        _uiState.update { state ->
            state.copy(
                items = state.items + added,
                pending = PendingFood(choice = null, snapshot = item, unit = FoodUnit.MEAL_PORTION, amountText = item.amount.formatAmount(), editingKey = added.key),
                pendingInvalid = false,
            )
        }
    }

    private fun confirmPending() {
        val state = _uiState.value
        val pending = state.pending ?: return
        val amount = FoodAmount.parse(pending.amountText, pending.unit)
        if (amount == null) {
            _uiState.update { it.copy(pendingInvalid = true) }
            return
        }
        val editing = pending.editingKey?.let { key -> state.items.firstOrNull { it.key == key } }
        val item = buildItem(pending, editing?.item, amount)
        _uiState.update { current ->
            val items = if (editing != null) current.items.map { if (it.key == editing.key) it.copy(item = item) else it } else current.items + keyed(item)
            current.copy(items = items, pending = null, pendingInvalid = false, query = "", search = BuilderSearch.Idle)
        }
        searchJob?.cancel()
        onlineJob?.cancel()
    }

    private fun buildItem(pending: PendingFood, existing: MealLogItem?, amount: Double): MealLogItem {
        val choice = pending.choice
        val base = existing ?: pending.snapshot
        return when {
            base != null && pending.unit == base.unit -> base.withAmount(amount)
            choice != null -> MealLogItem(
                foodKey = base?.foodKey ?: choice.key,
                name = base?.name ?: choice.name,
                amount = amount,
                unit = pending.unit,
                macros = FoodAmount.macrosFor(choice.per100g, amount, pending.unit),
            )
            base != null -> base.withAmount(amount)
            else -> error("A pending food needs a choice or a snapshot") // not UI text: programming error
        }
    }

    private fun logMealPortion(text: String) {
        val state = _uiState.value
        val meal = state.pendingMeal ?: return
        if (state.isSaving) return
        val portion = FoodAmount.parse(text, FoodUnit.MEAL_PORTION)
        if (portion == null) {
            _uiState.update { it.copy(pendingInvalid = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true, pendingInvalid = false) }
        viewModelScope.launch {
            try {
                val award = writeMutex.withLock { nutrition.logOffPlan(date, meal.id, portion) }
                _events.send(MealBuilderEvent.Finished(award))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(MealBuilderEvent.Message(failedText(R.string.food_day_save_failed, error)))
            }
        }
    }

    /** isSaving stays true after success so a second tap during the exit animation can't save twice. */
    private fun done(name: String) {
        val state = _uiState.value
        if (!state.canFinish) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val award = writeMutex.withLock { nutrition.saveQuickMeal(date, logId, name, state.items.map { it.item }) }
                _events.send(MealBuilderEvent.Finished(award))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(MealBuilderEvent.Message(failedText(R.string.food_day_save_failed, error)))
            }
        }
    }

    private fun saveAsMeal(name: String, recipeText: String) {
        val state = _uiState.value
        if (logId == null || state.items.isEmpty()) return
        val total = state.total
        viewModelScope.launch {
            if (IngredientCalculator.exceedsEditorLimits(total)) {
                _events.send(MealBuilderEvent.Message(uiText(R.string.food_calc_over_limit)))
                return@launch
            }
            try {
                val draft = MealDraft(null, name.trim(), total, tags = emptyList(), recipeText = recipeText.trim().ifEmpty { null }, recipeUrl = null)
                writeMutex.withLock { nutrition.saveMeal(draft) }
                _events.send(MealBuilderEvent.Message(uiText(R.string.food_builder_saved_as_meal)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(MealBuilderEvent.Message(failedText(R.string.food_meal_save_failed, error)))
            }
        }
    }

    companion object {
        const val ARG_DATE = "epochDay" // not UI text: navigation argument key
        const val ARG_LOG_ID = "logId" // not UI text: navigation argument key
        const val DEBOUNCE_MS = 250L
        const val ONLINE_DEBOUNCE_MS = 400L
        const val ONLINE_MIN_CHARS = 3
        const val ONLINE_LOCAL_ENOUGH = 5

        private fun SavedStateHandle.numberArg(key: String): Long? = when (val value = get<Any>(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }
    }
}
