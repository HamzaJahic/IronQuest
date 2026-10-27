package com.ironquest.app.ui.workout

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.domain.progression.formatKg
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.AddLink
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

/** Add a workout to a past day, or edit / delete a stored one. [onDone] leaves the screen after Save or Delete. */
@Composable
fun WorkoutLogEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: WorkoutLogEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                WorkoutLogEvent.Saved, WorkoutLogEvent.Deleted -> onDone()
                is WorkoutLogEvent.Message -> snackbar.showSnackbar(event.text.resolve(resources))
            }
        }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    WorkoutLogEditorContent(state, snackbar, onBack, viewModel::onAction, onDialogOpenChange = { open -> dialogOpen = open })
    if (!state.isLoading && !state.notFound) {
        OnboardingGate(OnboardingScreen.LOG_EDITOR, if (dialogOpen) setOf(OnboardingFact.BLOCKED) else emptySet())
    }
}

@Composable
private fun WorkoutLogEditorContent(
    state: WorkoutLogEditorUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (WorkoutLogEditorAction) -> Unit,
    onDialogOpenChange: (Boolean) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val currentOnDialogOpenChange by rememberUpdatedState(onDialogOpenChange)
    LaunchedEffect(confirmDelete || confirmDiscard) { currentOnDialogOpenChange(confirmDelete || confirmDiscard) }
    // isSaving also covers the exit animation after a successful save/delete: never prompt then.
    val guardBack = state.isDirty && !state.isSaving
    BackHandler(enabled = guardBack) { confirmDiscard = true }
    val handleBack = { if (guardBack) confirmDiscard = true else onBack() }
    val colors = IronTheme.colors
    val type = IronTheme.type
    val locale = currentLocale()

    Scaffold(
        containerColor = colors.paper,
        topBar = { ScreenTop(stringResource(if (state.isNew) R.string.workout_log_add_title else R.string.workout_log_edit_title), handleBack) },
        snackbarHost = { IronSnackbarHost(snackbar) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        if (state.notFound) {
            Text(stringResource(R.string.workout_log_unavailable), style = type.body, color = colors.muted, modifier = Modifier.padding(padding).padding(20.dp))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                listOfNotNull(
                    state.title?.resolve()?.takeIf { it.isNotEmpty() },
                    state.date?.let { Formats.date(it, stringResource(R.string.workout_log_date_pattern), locale) },
                ).joinToString(stringResource(R.string.common_dot_separator)),
                style = type.bodyStrong,
                color = colors.ink,
            )
            state.lastTime?.let { lastTime ->
                Text(stringResource(R.string.workout_log_last_time, Formats.date(lastTime, stringResource(R.string.workout_log_last_time_pattern), locale)), style = type.secondary, color = colors.muted)
            }
            val copyAnchor = state.exercises.firstOrNull { it.ghost.isNotEmpty() }
            state.exercises.forEach { ex ->
                ExerciseEditor(
                    ex,
                    isCopyAnchor = ex == copyAnchor,
                    onWeight = { row, text -> onAction(WorkoutLogEditorAction.WeightChange(ex.exercise.id, row, text)) },
                    onAmount = { row, text -> onAction(WorkoutLogEditorAction.AmountChange(ex.exercise.id, row, text)) },
                    onRemove = { row -> onAction(WorkoutLogEditorAction.RemoveSet(ex.exercise.id, row)) },
                    onAdd = { onAction(WorkoutLogEditorAction.AddSet(ex.exercise.id)) },
                    onCopyLast = { onAction(WorkoutLogEditorAction.CopyLastTime(ex.exercise.id)) },
                )
            }
            state.error?.let { Text(it.resolve(), style = type.secondary, color = colors.danger) }
            PrimaryButton(stringResource(R.string.workout_log_save), onClick = { onAction(WorkoutLogEditorAction.Save) }, enabled = !state.isSaving, modifier = Modifier.padding(top = 8.dp).onboardingAnchor(OnboardingTarget.LOG_EDITOR_SAVE))
            if (!state.isNew) {
                TextAction(stringResource(R.string.workout_log_delete), { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally), tone = ActionTone.DANGER)
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
    if (confirmDelete) {
        IronDialog(
            title = stringResource(R.string.workout_log_delete_title),
            text = stringResource(R.string.workout_log_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                onAction(WorkoutLogEditorAction.Delete)
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun ExerciseEditor(
    ex: LogExerciseUi,
    isCopyAnchor: Boolean,
    onWeight: (Int, String) -> Unit,
    onAmount: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onCopyLast: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(exerciseName(ex.exercise), style = type.bodyStrong, color = colors.ink)
                Text(
                    stringResource(if (ex.isCardio) R.string.workout_log_target_minutes else R.string.workout_log_target_reps, rangeText(ex.repRange)),
                    style = type.secondary,
                    color = colors.muted,
                )
            }
            if (ex.ghost.isNotEmpty()) {
                TextButton(onClick = onCopyLast, modifier = if (isCopyAnchor) Modifier.onboardingAnchor(OnboardingTarget.LOG_EDITOR_SAME_AS_LAST) else Modifier) { Text(stringResource(R.string.workout_log_copy_last), style = type.secondary, color = colors.ink) }
            }
        }
        ex.sets.forEachIndexed { i, row ->
            val ghost = ex.ghost.firstOrNull { it.setIndex == i }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (!ex.isCardio) {
                    IronTextField(
                        stringResource(R.string.workout_log_weight_label, i + 1),
                        row.weight,
                        { onWeight(i, it) },
                        modifier = Modifier.weight(1f),
                        placeholder = ghost?.weightKg?.formatKg(),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                IronTextField(
                    if (ex.isCardio) stringResource(R.string.workout_log_minutes_label, i + 1) else stringResource(R.string.workout_log_reps_label),
                    row.amount,
                    { onAmount(i, it) },
                    modifier = Modifier.weight(1f),
                    placeholder = (if (ex.isCardio) ghost?.durationMinutes else ghost?.reps)?.toString(),
                    keyboardType = KeyboardType.Number,
                )
                TextAction("✕", { onRemove(i) }, modifier = Modifier.padding(top = 6.dp), tone = ActionTone.MUTED)
            }
        }
        AddLink(stringResource(R.string.workout_add_set), onAdd)
    }
}
