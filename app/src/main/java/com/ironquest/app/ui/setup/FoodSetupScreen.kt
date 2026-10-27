package com.ironquest.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.ui.food.TargetField
import com.ironquest.app.ui.kit.BackHeader
import com.ironquest.app.ui.kit.CheckBox
import com.ironquest.app.ui.kit.ChoiceChips
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PaletteSwatches
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.you.labelRes

/**
 * First-launch setup and the "Food tracking" settings page (same screen, [FoodSetupUiState.fromSettings]).
 * [onExit] navigates away; [onBack] leaves the settings page without choosing.
 */
@Composable
fun FoodSetupScreen(
    onExit: (SetupExit, fromSettings: Boolean) -> Unit,
    onBack: () -> Unit,
    viewModel: FoodSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FoodSetupEvent.Exit -> onExit(event.next, viewModel.uiState.value.fromSettings)
            }
        }
    }
    val onAction = remember(viewModel) { viewModel::onAction }
    FoodSetupContent(state, onAction, onBack)
}

@Composable
fun FoodSetupContent(state: FoodSetupUiState, onAction: (FoodSetupAction) -> Unit, onBack: () -> Unit) {
    // Back steps within the setup; on its first step (and on the settings page's) it leaves instead.
    val inSubStep = when (state.step) {
        SetupStep.APPEARANCE -> false
        SetupStep.CHOOSE -> !state.fromSettings
        SetupStep.PLAN_NEXT, SetupStep.TARGETS -> true
    }
    BackHandler(enabled = inSubStep) { onAction(FoodSetupAction.Back) }
    Column(Modifier.fillMaxSize()) {
        if (inSubStep) {
            BackHeader(onBack = { onAction(FoodSetupAction.Back) })
        } else if (state.fromSettings) {
            BackHeader(onBack = onBack)
        } else {
            Spacer(Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state.step) {
                SetupStep.APPEARANCE -> AppearanceStep(state, onAction)
                SetupStep.CHOOSE -> ChooseStep(state, onAction)
                SetupStep.PLAN_NEXT -> PlanNextStep(onAction)
                SetupStep.TARGETS -> TargetsStep(state, onAction)
            }
        }
    }
}

@Composable
private fun AppearanceStep(state: FoodSetupUiState, onAction: (FoodSetupAction) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val resources = LocalContext.current.resources
    LocalConfiguration.current // re-resolve the labels when the app language changes
    fun label(@StringRes id: Int) = resources.getString(id)
    PageTitle(stringResource(R.string.setup_appearance_title))
    Text(stringResource(R.string.setup_appearance_intro), style = type.body, color = colors.muted)
    Text(stringResource(R.string.setup_appearance_style), style = type.secondary, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
    ChoiceChips(AppStyle.entries, state.appearance.style, { label(it.labelRes()) }, { onAction(FoodSetupAction.SetStyle(it)) })
    Text(
        stringResource(R.string.setup_appearance_palette, label(state.appearance.palette.labelRes())),
        style = type.secondary,
        color = colors.muted,
        modifier = Modifier.padding(top = 8.dp),
    )
    PaletteSwatches(state.appearance.palette, { label(it.labelRes()) }, { onAction(FoodSetupAction.SetPalette(it)) })
    Text(stringResource(R.string.setup_appearance_preview), style = type.secondary, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
    TodayPreview()
    PrimaryButton(stringResource(R.string.setup_appearance_continue), { onAction(FoodSetupAction.Continue) }, Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
private fun ChooseStep(state: FoodSetupUiState, onAction: (FoodSetupAction) -> Unit) {
    PageTitle(stringResource(if (state.fromSettings) R.string.setup_food_settings_title else R.string.setup_food_title))
    if (!state.fromSettings) {
        Text(stringResource(R.string.setup_food_intro), style = IronTheme.type.body, color = IronTheme.colors.muted)
    }
    RowGroup {
        ModeRow(R.string.setup_food_mode_plan, R.string.setup_food_mode_plan_hint, state.mode == FoodTrackingMode.PLAN) {
            onAction(FoodSetupAction.Choose(FoodTrackingMode.PLAN))
        }
        ModeRow(R.string.setup_food_mode_on_the_fly, R.string.setup_food_mode_on_the_fly_hint, state.mode == FoodTrackingMode.ON_THE_FLY) {
            onAction(FoodSetupAction.Choose(FoodTrackingMode.ON_THE_FLY))
        }
        ModeRow(R.string.setup_food_mode_both, R.string.setup_food_mode_both_hint, state.mode == FoodTrackingMode.BOTH) {
            onAction(FoodSetupAction.Choose(FoodTrackingMode.BOTH))
        }
    }
}

@Composable
private fun ModeRow(@StringRes title: Int, @StringRes hint: Int, selected: Boolean, onClick: () -> Unit) {
    val selectedLabel = stringResource(R.string.setup_food_mode_selected_cd)
    RuledRow(onClick = onClick, modifier = Modifier.semantics { if (selected) contentDescription = selectedLabel }) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(stringResource(title), style = IronTheme.type.bodyStrong, color = IronTheme.colors.ink)
            Text(stringResource(hint), style = IronTheme.type.secondary, color = IronTheme.colors.muted)
        }
        CheckBox(checked = selected)
    }
}

@Composable
private fun PlanNextStep(onAction: (FoodSetupAction) -> Unit) {
    PageTitle(stringResource(R.string.setup_food_plan_title))
    Text(stringResource(R.string.setup_food_plan_body), style = IronTheme.type.body, color = IronTheme.colors.muted)
    PrimaryButton(stringResource(R.string.setup_food_plan_import), { onAction(FoodSetupAction.Finish(SetupExit.IMPORT_PLAN)) }, Modifier.fillMaxWidth())
    SecondaryButton(stringResource(R.string.setup_food_plan_build), { onAction(FoodSetupAction.Finish(SetupExit.NEW_PLAN)) }, Modifier.fillMaxWidth())
    SecondaryButton(stringResource(R.string.setup_food_plan_later), { onAction(FoodSetupAction.Finish(SetupExit.TODAY)) }, Modifier.fillMaxWidth())
}

@Composable
private fun TargetsStep(state: FoodSetupUiState, onAction: (FoodSetupAction) -> Unit) {
    val error = stringResource(R.string.setup_food_targets_error)
    PageTitle(stringResource(R.string.setup_food_targets_title))
    Text(stringResource(R.string.setup_food_targets_body), style = IronTheme.type.body, color = IronTheme.colors.muted)
    IronTextField(
        stringResource(R.string.food_targets_kcal),
        state.texts.kcal,
        { onAction(FoodSetupAction.TargetChanged(TargetField.KCAL, it)) },
        Modifier.fillMaxWidth(),
        isError = state.targetsInvalid,
        keyboardType = KeyboardType.Decimal,
    )
    IronTextField(
        stringResource(R.string.food_field_protein_g),
        state.texts.protein,
        { onAction(FoodSetupAction.TargetChanged(TargetField.PROTEIN, it)) },
        Modifier.fillMaxWidth(),
        isError = state.targetsInvalid,
        keyboardType = KeyboardType.Decimal,
    )
    IronTextField(
        stringResource(R.string.food_targets_water),
        state.texts.water,
        { onAction(FoodSetupAction.TargetChanged(TargetField.WATER, it)) },
        Modifier.fillMaxWidth(),
        isError = state.targetsInvalid,
        errorText = error.takeIf { state.targetsInvalid },
        keyboardType = KeyboardType.Number,
    )
    PrimaryButton(stringResource(R.string.setup_food_targets_save), { onAction(FoodSetupAction.SaveTargets) }, Modifier.fillMaxWidth())
    SecondaryButton(stringResource(R.string.setup_food_targets_skip), { onAction(FoodSetupAction.Skip) }, Modifier.fillMaxWidth())
}
