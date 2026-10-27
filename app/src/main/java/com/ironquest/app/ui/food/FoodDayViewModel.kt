package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.DaySummary
import com.ironquest.app.domain.nutrition.DaySummaryCalculator
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.nutrition.SupplementDraft
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class SlotRowUi(val slot: PlanSlot, val log: MealLogEntry?, val isSaving: Boolean = false) {
    val checked: Boolean get() = log != null
}

data class FoodDayUiState(
    val isLoading: Boolean = true,
    val date: LocalDate,
    /** Latest day that can be shown or picked. */
    val today: LocalDate = date,
    val isToday: Boolean = true,
    val planName: String? = null,
    val summary: DaySummary? = null,
    val waterMl: Int = 0,
    val waterTargetMl: Int? = null,
    val slots: List<SlotRowUi> = emptyList(),
    /** Slot logs whose slot isn't in the active plan (plan switched mid-day). */
    val earlier: List<MealLogEntry> = emptyList(),
    /** Off-plan logs without foods (picked from the library). */
    val offPlan: List<MealLogEntry> = emptyList(),
    /** Meals built from foods ("Log food"). */
    val quickMeals: List<MealLogEntry> = emptyList(),
    val mode: FoodTrackingMode = FoodTrackingMode.DEFAULT,
    val supplements: List<SupplementStatus> = emptyList(),
    /** For the "log other meal" picker. */
    val meals: List<Meal> = emptyList(),
) {
    val canGoForward: Boolean get() = !isToday

    fun onboardingFacts(dialogOpen: Boolean): Set<OnboardingFact> = buildSet {
        if (supplements.isNotEmpty()) add(OnboardingFact.SUPPLEMENTS_VISIBLE)
        if (mode.showsPlan) add(OnboardingFact.FOOD_PLAN_SHOWN)
        add(if (mode.showsBuilder) OnboardingFact.FOOD_BUILDER_SHOWN else OnboardingFact.FOOD_PICKER_SHOWN)
        if (dialogOpen) add(OnboardingFact.BLOCKED)
    }
}

/** Everything the Food Day view can ask the ViewModel to do. */
sealed interface FoodDayAction {
    data object PreviousDay : FoodDayAction
    data object NextDay : FoodDayAction
    data class ShowDate(val target: LocalDate) : FoodDayAction
    data object RefreshToday : FoodDayAction
    data class ToggleSlot(val slotId: Long) : FoodDayAction
    data class SetPortion(val logId: Long, val text: String) : FoodDayAction
    data class LogOffPlan(val mealId: Long, val portionText: String = "1") : FoodDayAction
    data class RemoveLog(val logId: Long) : FoodDayAction
    data object AddWater : FoodDayAction
    data object RemoveWater : FoodDayAction
    data class ToggleSupplement(val id: Long) : FoodDayAction
    data class SaveSupplement(val id: Long?, val name: String, val dose: String, val reminderTimeMin: Int? = null) : FoodDayAction
    data class DeleteSupplement(val id: Long) : FoodDayAction
    data class SetOwnTargets(val targets: MacroTargets) : FoodDayAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodDayViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Read on demand, not cached: the screen may stay open past midnight. */
    private fun today(): LocalDate = LocalDate.now(clock)

    private val date = MutableStateFlow(today())

    /** Slots with a write in flight; further taps on them are ignored (double-tap guard). */
    private val savingSlots = MutableStateFlow<Set<Long>>(emptySet())

    private val _events = Channel<FoodEvent>(Channel.BUFFERED)
    val events: Flow<FoodEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    val uiState: StateFlow<FoodDayUiState> = date
        .flatMapLatest { selectedDate ->
            combine(
                repository.observeDay(selectedDate),
                repository.observeMeals(),
                savingSlots,
                settings.observeFood(),
            ) { day, meals, saving, food ->
                buildState(day, meals, today(), saving, food.mode)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDayUiState(date = today()))

    fun onAction(action: FoodDayAction) {
        when (action) {
            FoodDayAction.PreviousDay -> previousDay()
            FoodDayAction.NextDay -> nextDay()
            is FoodDayAction.ShowDate -> showDate(action.target)
            FoodDayAction.RefreshToday -> refreshToday()
            is FoodDayAction.ToggleSlot -> toggleSlot(action.slotId)
            is FoodDayAction.SetPortion -> setPortion(action.logId, action.text)
            is FoodDayAction.LogOffPlan -> logOffPlan(action.mealId, action.portionText)
            is FoodDayAction.RemoveLog -> removeLog(action.logId)
            FoodDayAction.AddWater -> addWater()
            FoodDayAction.RemoveWater -> removeWater()
            is FoodDayAction.ToggleSupplement -> toggleSupplement(action.id)
            is FoodDayAction.SaveSupplement -> saveSupplement(action.id, action.name, action.dose, action.reminderTimeMin)
            is FoodDayAction.DeleteSupplement -> deleteSupplement(action.id)
            is FoodDayAction.SetOwnTargets -> settings.setOwnTargets(action.targets)
        }
    }

    /** True while the user hasn't navigated away from today, so the view should roll over at midnight. */
    private var followToday = true

    private fun previousDay() {
        followToday = false
        date.update { it.minusDays(1) }
    }

    private fun nextDay() {
        date.update { if (it < today()) it.plusDays(1) else it }
        followToday = date.value == today()
    }

    /** Used by the Week/Month views. */
    private fun showDate(target: LocalDate) {
        if (target.isAfter(today())) return
        date.value = target
        followToday = target == today()
    }

    /**
     * Moves to the new "today" if midnight passed while the user was following today.
     * The Day screen calls this from a LifecycleResumeEffect.
     */
    private fun refreshToday() {
        val now = today()
        if (followToday && date.value != now) date.value = now
    }

    private fun toggleSlot(slotId: Long) {
        if (slotId in savingSlots.value) return
        val state = uiState.value
        val row = state.slots.firstOrNull { it.slot.id == slotId } ?: return
        val day = state.date
        savingSlots.update { it + slotId }
        write(onFinally = { savingSlots.update { it - slotId } }) {
            val log = row.log
            if (log != null) repository.removeLog(log.id) else repository.logSlot(day, slotId, 1.0)
        }
    }

    private fun setPortion(logId: Long, text: String) {
        val portion = NutritionRules.parsePortion(text) ?: run {
            _events.trySend(FoodEvent.Message(uiText(R.string.food_day_portion_range)))
            return
        }
        write { repository.updatePortion(logId, portion) }
    }

    private fun logOffPlan(mealId: Long, portionText: String = "1") {
        val portion = NutritionRules.parsePortion(portionText) ?: run {
            _events.trySend(FoodEvent.Message(uiText(R.string.food_day_portion_range)))
            return
        }
        val day = uiState.value.date
        write { repository.logOffPlan(day, mealId, portion) }
    }

    private fun removeLog(logId: Long) = write { repository.removeLog(logId) }

    private fun addWater() {
        if (uiState.value.waterMl >= NutritionRules.MAX_WATER_DAY_ML) return
        val day = uiState.value.date
        write { repository.addWater(day, NutritionRules.WATER_STEP_ML) }
    }

    private fun removeWater() {
        val state = uiState.value
        if (state.waterMl <= 0) return
        val day = state.date
        write { repository.addWater(day, -NutritionRules.WATER_STEP_ML) }
    }

    private fun toggleSupplement(id: Long) {
        val state = uiState.value
        val supplement = state.supplements.firstOrNull { it.id == id } ?: return
        val day = state.date
        write { repository.setSupplementTaken(day, id, !supplement.taken) }
    }

    /** [reminderTimeMin] is stored as given - pass the current value when editing to keep it. */
    private fun saveSupplement(id: Long?, name: String, dose: String, reminderTimeMin: Int? = null) {
        if (name.isBlank()) {
            _events.trySend(FoodEvent.Message(uiText(R.string.food_day_supplement_name_required)))
            return
        }
        if (!NutritionRules.isValidReminder(reminderTimeMin, reminderOffsetMin = null)) {
            _events.trySend(FoodEvent.Message(uiText(R.string.food_day_reminder_invalid)))
            return
        }
        write {
            repository.saveSupplement(SupplementDraft(id, name.trim(), dose.trim(), reminderTimeMin))
            NutritionAward.NONE
        }
    }

    private fun deleteSupplement(id: Long) = write {
        repository.deleteSupplement(id)
        NutritionAward.NONE
    }

    /** Every write goes through [writeMutex], like ActiveWorkoutViewModel. */
    private fun write(onFinally: () -> Unit = {}, block: suspend () -> NutritionAward) {
        viewModelScope.launch {
            try {
                val award = writeMutex.withLock { block() }
                _events.sendAward(award)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(FoodEvent.Message(saveFailed(error)))
            } finally {
                onFinally()
            }
        }
    }

    companion object {
        /** "Couldn't save: <system message>", or the "unknown error" wording when the exception has none. */
        internal fun saveFailed(error: Exception): UiText = error.message?.let { uiText(R.string.food_day_save_failed, UiText.Verbatim(it)) }
            ?: uiText(R.string.food_day_save_failed_unknown)

        /** Pure so it can be unit-tested; splits the day's logs into checklist / earlier / off-plan / quick meals. */
        fun buildState(
            day: DayNutrition,
            meals: List<Meal>,
            today: LocalDate,
            saving: Set<Long>,
            mode: FoodTrackingMode = FoodTrackingMode.DEFAULT,
        ): FoodDayUiState {
            val plan = day.activePlan
            val slotIds = plan?.slots?.map { it.id }?.toSet().orEmpty()
            return FoodDayUiState(
                isLoading = false,
                date = day.date,
                today = today,
                isToday = day.date == today,
                planName = plan?.name,
                summary = DaySummaryCalculator.summarize(day.logs, day.targets),
                waterMl = day.waterMl,
                waterTargetMl = day.targets?.waterMl,
                slots = plan?.slots.orEmpty().takeIf { mode.showsPlan }.orEmpty().map { slot ->
                    SlotRowUi(slot, day.logs.firstOrNull { it.slotId == slot.id }, isSaving = slot.id in saving)
                },
                earlier = day.logs.filter { mode.showsPlan && it.slotId != null && it.slotId !in slotIds },
                // Without the checklist, planned meals logged before a mode switch stay visible (and removable) as plain logs.
                offPlan = day.logs.filter { !it.isQuickMeal && (it.slotId == null || !mode.showsPlan) },
                quickMeals = day.logs.filter { it.isQuickMeal },
                mode = mode,
                supplements = day.supplements,
                meals = meals,
            )
        }
    }
}
