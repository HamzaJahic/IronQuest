package com.ironquest.app.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.ui.food.DailyTargetsForm
import com.ironquest.app.ui.food.DailyTargetsTexts
import com.ironquest.app.ui.food.TargetField
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class SetupStep { CHOOSE, PLAN_NEXT, TARGETS }

/** Where the app goes once the setup (or the settings page) is done. */
enum class SetupExit { TODAY, IMPORT_PLAN, NEW_PLAN }

data class FoodSetupUiState(
    val step: SetupStep = SetupStep.CHOOSE,
    /** Null on first launch (nothing chosen yet); the current setting when opened from the You tab. */
    val mode: FoodTrackingMode? = null,
    val fromSettings: Boolean = false,
    val texts: DailyTargetsTexts = DailyTargetsTexts(),
    val targetsInvalid: Boolean = false,
)

sealed interface FoodSetupAction {
    data class Choose(val mode: FoodTrackingMode) : FoodSetupAction
    data object Back : FoodSetupAction
    data class TargetChanged(val field: TargetField, val text: String) : FoodSetupAction
    data object SaveTargets : FoodSetupAction
    data object Skip : FoodSetupAction
    data class Finish(val next: SetupExit) : FoodSetupAction
}

sealed interface FoodSetupEvent {
    data class Exit(val next: SetupExit) : FoodSetupEvent
}

@HiltViewModel
class FoodSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val fromSettings: Boolean = savedStateHandle[ARG_FROM_SETTINGS] ?: false

    private val _uiState = MutableStateFlow(
        FoodSetupUiState(mode = if (fromSettings) settings.currentFood().mode else null, fromSettings = fromSettings),
    )
    val uiState: StateFlow<FoodSetupUiState> = _uiState.asStateFlow()

    private val _events = Channel<FoodSetupEvent>(Channel.BUFFERED)
    val events: Flow<FoodSetupEvent> = _events.receiveAsFlow()

    fun onAction(action: FoodSetupAction) {
        when (action) {
            is FoodSetupAction.Choose -> choose(action.mode)
            FoodSetupAction.Back -> _uiState.update { it.copy(step = SetupStep.CHOOSE, targetsInvalid = false) }
            is FoodSetupAction.TargetChanged -> _uiState.update { it.copy(texts = it.texts.with(action.field, action.text), targetsInvalid = false) }
            FoodSetupAction.SaveTargets -> saveTargets()
            FoodSetupAction.Skip -> finish(SetupExit.TODAY)
            is FoodSetupAction.Finish -> finish(action.next)
        }
    }

    private fun choose(mode: FoodTrackingMode) {
        settings.setFoodTrackingMode(mode)
        when {
            mode == FoodTrackingMode.ON_THE_FLY -> _uiState.update {
                it.copy(mode = mode, step = SetupStep.TARGETS, texts = DailyTargetsForm.texts(settings.currentFood().ownTargets), targetsInvalid = false)
            }
            fromSettings -> {
                _uiState.update { it.copy(mode = mode) }
                _events.trySend(FoodSetupEvent.Exit(SetupExit.TODAY))
            }
            else -> _uiState.update { it.copy(mode = mode, step = SetupStep.PLAN_NEXT) }
        }
    }

    private fun saveTargets() {
        val parsed = DailyTargetsForm.parse(_uiState.value.texts)
        if (parsed == null) {
            _uiState.update { it.copy(targetsInvalid = true) }
            return
        }
        // Fiber has no field here; keep whatever was stored.
        settings.setOwnTargets(parsed.copy(fiberG = settings.currentFood().ownTargets.fiberG))
        finish(SetupExit.TODAY)
    }

    private fun finish(next: SetupExit) {
        if (!fromSettings) settings.finishFoodSetup()
        _events.trySend(FoodSetupEvent.Exit(next))
    }

    private fun DailyTargetsTexts.with(field: TargetField, text: String) = when (field) {
        TargetField.KCAL -> copy(kcal = text)
        TargetField.PROTEIN -> copy(protein = text)
        TargetField.CARBS -> copy(carbs = text)
        TargetField.FAT -> copy(fat = text)
        TargetField.WATER -> copy(water = text)
    }

    companion object {
        const val ARG_FROM_SETTINGS = "fromSettings" // not UI text: navigation argument key
    }
}
