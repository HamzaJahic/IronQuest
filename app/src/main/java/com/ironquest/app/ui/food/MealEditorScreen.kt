package com.ironquest.app.ui.food

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.DiscardChangesDialog
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun MealEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onDeleted: (DeleteOutcome) -> Unit,
    viewModel: MealEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                MealEditorEvent.Saved -> onDone()
                is MealEditorEvent.Deleted -> onDeleted(event.outcome)
                is MealEditorEvent.Message -> {
                    snackbar.showSnackbar(event.text.resolve(resources))
                }
            }
        }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    MealEditorContent(state, snackbar, onBack = onBack, onAction = viewModel::onAction, onDialogOpenChange = { open -> dialogOpen = open })
    // The ingredient sheet counts as a dialog here: it runs its own gate.
    if (!state.isLoading) OnboardingGate(OnboardingScreen.MEAL_EDITOR, if (dialogOpen) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@Composable
private fun MealEditorContent(
    state: MealEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (MealEditorAction) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    val onFormChange: (MealForm) -> Unit = { onAction(MealEditorAction.FormChange(it)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCalculator by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    val dialogOpen = confirmDelete || showCalculator || confirmDiscard
    LaunchedEffect(dialogOpen) { currentOnDialogOpenChange(dialogOpen) }
    // isSaving also covers the exit animation after a successful save/delete: never prompt then.
    val guardBack = state.isDirty && !state.isSaving
    BackHandler(enabled = guardBack) { confirmDiscard = true }
    val handleBack = { if (guardBack) confirmDiscard = true else onBack() }
    val form = state.form
    val errors = state.errors
    Scaffold(
        containerColor = IronTheme.colors.paper,
        topBar = { ScreenTop(stringResource(if (state.isNew) R.string.food_meal_editor_title_new else R.string.food_meal_editor_title_edit), handleBack) },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The name and macros, as one coach-mark target.
            Column(Modifier.onboardingAnchor(OnboardingTarget.MEAL_EDITOR_MACROS), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IronTextField(stringResource(R.string.food_field_name), form.name, { onFormChange(form.copy(name = it)) }, isError = MealField.NAME in errors, errorText = stringResource(R.string.food_error_required))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IronTextField(stringResource(R.string.food_field_protein_g), form.protein, { onFormChange(form.copy(protein = it)) }, modifier = Modifier.weight(1f), isError = MealField.PROTEIN in errors, errorText = stringResource(R.string.food_error_grams_range), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_carbs_g), form.carbs, { onFormChange(form.copy(carbs = it)) }, modifier = Modifier.weight(1f), isError = MealField.CARBS in errors, errorText = stringResource(R.string.food_error_grams_range), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_fat_g), form.fat, { onFormChange(form.copy(fat = it)) }, modifier = Modifier.weight(1f), isError = MealField.FAT in errors, errorText = stringResource(R.string.food_error_grams_range), keyboardType = KeyboardType.Decimal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IronTextField(stringResource(R.string.food_field_fiber_g), form.fiber, { onFormChange(form.copy(fiber = it)) }, modifier = Modifier.weight(1f), isError = MealField.FIBER in errors, errorText = stringResource(R.string.food_error_grams_range), keyboardType = KeyboardType.Decimal)
                    IronTextField(
                        stringResource(R.string.food_field_kcal_optional),
                        form.kcal,
                        { onFormChange(form.copy(kcal = it)) },
                        modifier = Modifier.weight(1f),
                        isError = MealField.KCAL in errors,
                        errorText = stringResource(R.string.food_error_kcal_range),
                        placeholder = state.derivedKcalHint?.resolve(),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
            }
            TextAction(stringResource(R.string.food_meal_editor_estimate), { showCalculator = true }, modifier = Modifier.align(Alignment.End).onboardingAnchor(OnboardingTarget.MEAL_EDITOR_ESTIMATE), strong = true)
            IronTextField(stringResource(R.string.food_meal_editor_tags), form.tags, { onFormChange(form.copy(tags = it)) })
            IronTextField(
                stringResource(R.string.food_meal_editor_recipe),
                form.recipeText,
                { onFormChange(form.copy(recipeText = it)) },
                singleLine = false,
                minLines = 4,
            )
            IronTextField(
                stringResource(R.string.food_meal_editor_recipe_link),
                form.recipeUrl,
                { onFormChange(form.copy(recipeUrl = it)) },
                isError = MealField.URL in errors,
                errorText = stringResource(R.string.food_meal_editor_link_error),
                keyboardType = KeyboardType.Uri,
            )
            PrimaryButton(stringResource(R.string.food_meal_editor_save), onClick = { onAction(MealEditorAction.Save) }, enabled = !state.isSaving, modifier = Modifier.padding(top = 8.dp))
            if (!state.isNew) {
                TextAction(stringResource(R.string.food_meal_editor_delete), { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally), tone = ActionTone.DANGER)
            }
        }
    }
    if (showCalculator) {
        IngredientCalculatorSheet(
            // The sheet hides itself, then calls onDismiss.
            onApply = { onAction(MealEditorAction.ApplyEstimate(it)) },
            onDismiss = { showCalculator = false },
        )
    }
    if (confirmDiscard) {
        DiscardChangesDialog(
            onDiscard = {
                confirmDiscard = false
                onBack()
            },
            onKeepEditing = { confirmDiscard = false },
        )
    }
    if (confirmDelete) {
        IronDialog(
            title = stringResource(R.string.food_meal_editor_delete_title),
            text = stringResource(R.string.food_meal_editor_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                onAction(MealEditorAction.Delete)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}
