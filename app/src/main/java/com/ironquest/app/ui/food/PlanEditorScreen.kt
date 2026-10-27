package com.ironquest.app.ui.food

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.DiscardChangesDialog
import com.ironquest.app.ui.kit.IronIcons
import com.ironquest.app.ui.kit.IronSnackbarHost
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.ScreenTop
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.kit.TimeDialog
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PlanEditorScreen(onBack: () -> Unit, onDone: (NutritionAward) -> Unit, viewModel: PlanEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PlanEditorEvent.Saved -> onDone(event.award)
                is PlanEditorEvent.Message -> {
                    snackbar.showSnackbar(event.text.resolve(resources))
                }
            }
        }
    }
    val permissionGate = rememberReminderPermissionGate()
    val onAction: (PlanEditorAction) -> Unit = remember(viewModel, permissionGate) {
        { action ->
            if (action is PlanEditorAction.Save) {
                permissionGate(viewModel.uiState.value.hasReminders) { viewModel.onAction(action) }
            } else {
                viewModel.onAction(action)
            }
        }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    PlanEditorContent(state, snackbar, onBack = onBack, onAction = onAction, onDialogOpenChange = { open -> dialogOpen = open })
    if (!state.isLoading) OnboardingGate(OnboardingScreen.PLAN_EDITOR, if (dialogOpen) setOf(OnboardingFact.BLOCKED) else emptySet())
}

@Composable
private fun PlanEditorContent(
    state: PlanEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (PlanEditorAction) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    var pickingMealFor by remember { mutableStateOf<Long?>(null) }
    var pickingTimeFor by remember { mutableStateOf<SlotForm?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    val dialogOpen = pickingMealFor != null || pickingTimeFor != null || confirmDiscard
    LaunchedEffect(dialogOpen) { currentOnDialogOpenChange(dialogOpen) }
    // isSaving also covers the exit animation after a successful save: never prompt then.
    val guardBack = state.isDirty && !state.isSaving
    BackHandler(enabled = guardBack) { confirmDiscard = true }
    val handleBack = { if (guardBack) confirmDiscard = true else onBack() }
    val form = state.form
    val errors = state.fieldErrors
    val addSlotLabel = defaultSlotLabel(form.slots.size)

    Scaffold(
        containerColor = IronTheme.colors.paper,
        topBar = { ScreenTop(stringResource(if (state.isNew) R.string.food_plan_editor_title_new else R.string.food_plan_editor_title_edit), handleBack) },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IronTextField(stringResource(R.string.food_plan_editor_name), form.name, { onAction(PlanEditorAction.FormChange(form.copy(name = it))) }, isError = PlanField.NAME in errors, errorText = stringResource(R.string.food_error_required))
            }
            item { SectionHeader(stringResource(R.string.food_plan_editor_targets_header)) }
            item {
                Row(Modifier.onboardingAnchor(OnboardingTarget.PLAN_EDITOR_TARGETS), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IronTextField(stringResource(R.string.food_field_protein_g), form.protein, { onAction(PlanEditorAction.FormChange(form.copy(protein = it))) }, modifier = Modifier.weight(1f), isError = PlanField.PROTEIN in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_carbs_g), form.carbs, { onAction(PlanEditorAction.FormChange(form.copy(carbs = it))) }, modifier = Modifier.weight(1f), isError = PlanField.CARBS in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_fat_g), form.fat, { onAction(PlanEditorAction.FormChange(form.copy(fat = it))) }, modifier = Modifier.weight(1f), isError = PlanField.FAT in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IronTextField(stringResource(R.string.food_field_fiber_g), form.fiber, { onAction(PlanEditorAction.FormChange(form.copy(fiber = it))) }, modifier = Modifier.weight(1f), isError = PlanField.FIBER in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_kcal), form.kcal, { onAction(PlanEditorAction.FormChange(form.copy(kcal = it))) }, modifier = Modifier.weight(1f), isError = PlanField.KCAL in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                    IronTextField(stringResource(R.string.food_field_water_ml), form.water, { onAction(PlanEditorAction.FormChange(form.copy(water = it))) }, modifier = Modifier.weight(1f), isError = PlanField.WATER in errors, errorText = stringResource(R.string.food_error_invalid), keyboardType = KeyboardType.Decimal)
                }
            }
            item { SectionHeader(stringResource(R.string.food_plan_editor_meals_header)) }
            itemsIndexed(form.slots, key = { _, slot -> slot.key }) { index, slot ->
                SlotEditor(
                    slot = slot,
                    isError = slot.key in state.slotErrors,
                    isFirst = index == 0,
                    isLast = index == form.slots.lastIndex,
                    onAction = onAction,
                    onPickMeal = { pickingMealFor = slot.key },
                    onPickTime = { pickingTimeFor = slot },
                )
            }
            item {
                SecondaryButton(stringResource(R.string.food_plan_editor_add_meal), onClick = { onAction(PlanEditorAction.AddSlot(addSlotLabel)) }, modifier = Modifier.fillMaxWidth())
            }
            item { ReminderPermissionHints(active = state.hasReminders) }
            item {
                PrimaryButton(stringResource(R.string.food_plan_editor_save), onClick = { onAction(PlanEditorAction.Save) }, enabled = !state.isSaving)
            }
        }
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
    pickingMealFor?.let { key ->
        MealPickerSheet(
            meals = state.meals,
            onPick = { meal ->
                onAction(PlanEditorAction.UpdateSlot(key) { it.copy(mealId = meal.id, mealName = meal.name) })
                pickingMealFor = null
            },
            onDismiss = { pickingMealFor = null },
        )
    }
    pickingTimeFor?.let { slot ->
        TimeDialog(
            initialMin = slot.timeOfDayMin,
            onConfirm = { minutes ->
                onAction(PlanEditorAction.UpdateSlot(slot.key) { it.copy(timeOfDayMin = minutes) })
                pickingTimeFor = null
            },
            onDismiss = { pickingTimeFor = null },
        )
    }
}

@Composable
private fun SlotEditor(
    slot: SlotForm,
    isError: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onAction: (PlanEditorAction) -> Unit,
    onPickMeal: () -> Unit,
    onPickTime: () -> Unit,
) {
    val colors = IronTheme.colors
    val shape = IronTheme.shapes.tile
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.raised)
            .border(1.dp, if (isError) colors.danger else colors.line, shape)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IronTextField(stringResource(R.string.food_plan_editor_label), slot.label, { text -> onAction(PlanEditorAction.UpdateSlot(slot.key) { it.copy(label = text) }) }, modifier = Modifier.weight(1f))
                IconButton(onClick = { onAction(PlanEditorAction.MoveSlot(slot.key, -1)) }, enabled = !isFirst) {
                    Icon(IronIcons.ArrowUpward, contentDescription = stringResource(R.string.food_plan_editor_move_up_cd), tint = if (isFirst) colors.line else colors.muted)
                }
                IconButton(onClick = { onAction(PlanEditorAction.MoveSlot(slot.key, +1)) }, enabled = !isLast) {
                    Icon(IronIcons.ArrowDownward, contentDescription = stringResource(R.string.food_plan_editor_move_down_cd), tint = if (isLast) colors.line else colors.muted)
                }
                IconButton(onClick = { onAction(PlanEditorAction.RemoveSlot(slot.key)) }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.food_plan_editor_remove_meal_cd), tint = colors.danger)
                }
            }
            SecondaryButton(slot.mealName ?: stringResource(R.string.food_plan_editor_choose_meal), onClick = onPickMeal, modifier = Modifier.fillMaxWidth())
            Row(if (isFirst) Modifier.onboardingAnchor(OnboardingTarget.PLAN_EDITOR_SLOT) else Modifier, verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(slot.timeOfDayMin?.let { formatTimeOfDay(it) } ?: stringResource(R.string.food_plan_editor_set_time), onClick = onPickTime)
                if (slot.timeOfDayMin != null) {
                    TextAction(stringResource(R.string.common_clear), { onAction(PlanEditorAction.ClearSlotTime(slot.key)) }, tone = ActionTone.MUTED)
                }
            }
            ReminderPicker(slot) { offset -> onAction(PlanEditorAction.SetSlotReminder(slot.key, offset)) }
            if (isError) {
                Text(stringResource(R.string.food_plan_editor_slot_error), style = IronTheme.type.secondary, color = colors.danger)
            }
        }
    }
}

@Composable
private fun ReminderPicker(slot: SlotForm, onSelect: (Int?) -> Unit) {
    val colors = IronTheme.colors
    var open by remember { mutableStateOf(false) }
    val enabled = slot.timeOfDayMin != null
    val reminderCd = slot.reminderOffsetMin?.let { pluralStringResource(R.plurals.food_plan_editor_reminder_cd, it, it) }
        ?: stringResource(R.string.food_plan_editor_no_reminder_cd)
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            shape = IronTheme.shapes.button,
            border = BorderStroke(1.dp, if (enabled) colors.ink else colors.line),
            modifier = Modifier.semantics {
                if (enabled) {
                    contentDescription = reminderCd
                }
            },
        ) {
            Icon(
                if (slot.reminderOffsetMin != null) IronIcons.NotificationsActive else IronIcons.NotificationsOff,
                contentDescription = null,
                tint = if (enabled) colors.ink else colors.muted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (enabled) PlanEditorViewModel.reminderLabel(slot.reminderOffsetMin).resolve() else stringResource(R.string.food_plan_editor_reminder_needs_time),
                style = IronTheme.type.body,
                color = if (enabled) colors.ink else colors.muted,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.raised) {
            PlanEditorViewModel.REMINDER_CHOICES.forEach { choice ->
                val selected = choice == slot.reminderOffsetMin
                DropdownMenuItem(
                    modifier = Modifier.semantics { this.selected = selected },
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_selected_cd), tint = colors.accent) }
                    } else {
                        null
                    },
                    text = { Text(PlanEditorViewModel.reminderLabel(choice).resolve(), style = IronTheme.type.body, color = colors.ink) },
                    onClick = {
                        onSelect(choice)
                        open = false
                    },
                )
            }
        }
    }
}

/** The pre-filled name of the slot about to be added: Breakfast, Lunch, Snack, Dinner, then "Meal 5", "Meal 6", … */
@Composable
private fun defaultSlotLabel(index: Int): String = when (index) {
    0 -> stringResource(R.string.food_plan_slot_default_1)
    1 -> stringResource(R.string.food_plan_slot_default_2)
    2 -> stringResource(R.string.food_plan_slot_default_3)
    3 -> stringResource(R.string.food_plan_slot_default_4)
    else -> stringResource(R.string.food_plan_slot_default_n, index + 1)
}
