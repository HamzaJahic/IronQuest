package com.ironquest.app.ui.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.appearance.AppearanceSettings
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.domain.appearance.ThemeMode
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

/** [APPEARANCE] opens the first-launch setup only; the settings page starts at [CHOOSE]. */
enum class SetupStep { APPEARANCE, CHOOSE, PLAN_NEXT, TARGETS }

/** Where the app goes once the setup (or the settings page) is done. */
enum class SetupExit { TODAY, IMPORT_PLAN, NEW_PLAN }

data class FoodSetupUiState(
    val step: SetupStep = SetupStep.CHOOSE,
    /** Null on first launch (nothing chosen yet); the current setting when opened from the You tab. */
    val mode: FoodTrackingMode? = null,
    val fromSettings: Boolean = false,
    val texts: DailyTargetsTexts = DailyTargetsTexts(),
    val targetsInvalid: Boolean = false,
    val appearance: AppearanceSettings = AppearanceSettings.Default,
)

sealed interface FoodSetupAction {
    data class SetStyle(val style: AppStyle) : FoodSetupAction
    data class SetPalette(val palette: Palette) : FoodSetupAction
    data class SetThemeMode(val mode: ThemeMode) : FoodSetupAction
    data object Continue : FoodSetupAction
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
        FoodSetupUiState(
            step = if (fromSettings) SetupStep.CHOOSE else SetupStep.APPEARANCE,
            mode = if (fromSettings) settings.currentFood().mode else null,
            fromSettings = fromSettings,
            appearance = settings.currentAppearance(),
        ),
    )
    val uiState: StateFlow<FoodSetupUiState> = _uiState.asStateFlow()

    private val _events = Channel<FoodSetupEvent>(Channel.BUFFERED)
    val events: Flow<FoodSetupEvent> = _events.receiveAsFlow()

    fun onAction(action: FoodSetupAction) {
        when (action) {
            // Applied at once: MainActivity re-themes the whole app, this screen included.
            is FoodSetupAction.SetStyle -> {
                settings.setStyle(action.style)
                _uiState.update { it.copy(appearance = settings.currentAppearance()) }
            }
            is FoodSetupAction.SetPalette -> {
                settings.setPalette(action.palette)
                _uiState.update { it.copy(appearance = settings.currentAppearance()) }
            }
            is FoodSetupAction.SetThemeMode -> {
                settings.setThemeMode(action.mode)
                _uiState.update { it.copy(appearance = settings.currentAppearance()) }
            }
            FoodSetupAction.Continue -> _uiState.update { it.copy(step = SetupStep.CHOOSE) }
            is FoodSetupAction.Choose -> choose(action.mode)
            FoodSetupAction.Back -> _uiState.update { it.copy(step = previousStep(it.step), targetsInvalid = false) }
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

    private fun previousStep(step: SetupStep): SetupStep = when (step) {
        SetupStep.APPEARANCE -> SetupStep.APPEARANCE
        SetupStep.CHOOSE -> if (fromSettings) SetupStep.CHOOSE else SetupStep.APPEARANCE
        SetupStep.PLAN_NEXT, SetupStep.TARGETS -> SetupStep.CHOOSE
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
