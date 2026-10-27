package com.ironquest.app.ui.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.model.CompletedWorkoutLog
import com.ironquest.app.domain.model.ExerciseLog
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.catalog.templateName
import com.ironquest.app.ui.format.DatePatterns
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatDate
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.DateStepper
import com.ironquest.app.ui.kit.DayCell
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSheet
import com.ironquest.app.ui.kit.MonthGrid
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.RowAction
import com.ironquest.app.ui.kit.RowActions
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun LogScreen(
    contentPadding: PaddingValues,
    onAddWorkout: (templateId: String, date: LocalDate) -> Unit = { _, _ -> },
    onEditWorkout: (CompletedWorkoutLog) -> Unit = {},
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = IronTheme.colors
    val days = state.completedDays.size
    var confirmingDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        DateStepper(
            label = formatDate(state.month.atDay(1), DatePatterns.MONTH_YEAR),
            canGoForward = state.canGoForward,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            unit = stringResource(R.string.common_period_month),
        )
        PageTitle(stringResource(R.string.log_title))
        MonthCalendar(state, onDayClick = viewModel::selectDay)
        Text(
            pluralStringResource(R.plurals.log_month_summary_count, days, days, formatDate(state.month.atDay(1), DatePatterns.MONTH)),
            style = IronTheme.type.bodyStrong,
            color = colors.ink,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(stringResource(R.string.log_hint), style = IronTheme.type.secondary, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
    }

    state.selected?.let { detail ->
        DayDetailSheet(
            detail = detail,
            templates = state.templates,
            onDismiss = viewModel::dismissDay,
            onAdd = { templateId -> onAddWorkout(templateId, detail.date) },
            onEdit = onEditWorkout,
            onDelete = viewModel::deleteWorkout,
            onConfirmingChange = { confirming -> confirmingDelete = confirming },
        )
    }
    OnboardingGate(
        OnboardingScreen.LOG,
        buildSet {
            if (state.selected != null) add(OnboardingFact.DAY_SHEET_OPEN)
            if (state.selected != null && confirmingDelete) add(OnboardingFact.BLOCKED)
        },
    )
}

@Composable
private fun MonthCalendar(state: LogUiState, onDayClick: (LocalDate) -> Unit) {
    val colors = IronTheme.colors
    val descriptionPattern = stringResource(R.string.log_date_cd_pattern)
    val locale = currentLocale()
    MonthGrid(state.month, Modifier.padding(top = 12.dp).onboardingAnchor(OnboardingTarget.LOG_MONTH)) { date, cellModifier ->
        val completed = date in state.completedDays
        val isFuture = date.isAfter(state.today)
        DayCell(
            date = date,
            fill = if (completed) colors.accent else colors.paper,
            contentColor = when {
                completed -> colors.onAccent
                isFuture -> colors.line
                else -> colors.ink
            },
            isToday = date == state.today,
            isFuture = isFuture,
            description = Formats.date(date, descriptionPattern, locale).let { if (completed) stringResource(R.string.log_day_done_cd, it) else it },
            onClick = { onDayClick(date) },
            modifier = cellModifier,
            emphasized = completed || date == state.today,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailSheet(
    detail: DayDetailUi,
    templates: List<WorkoutTemplate>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onEdit: (CompletedWorkoutLog) -> Unit,
    onDelete: (Long) -> Unit,
    onConfirmingChange: (Boolean) -> Unit,
) {
    val colors = IronTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var picking by remember(detail.date) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<CompletedWorkoutLog?>(null) }
    val currentOnConfirmingChange by rememberUpdatedState(onConfirmingChange)
    LaunchedEffect(confirmDelete != null) { currentOnConfirmingChange(confirmDelete != null) }
    IronSheet(onDismiss = onDismiss, sheetState = sheetState) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Text(
                    Formats.date(detail.date, stringResource(R.string.log_date_full_pattern), currentLocale()),
                    style = IronTheme.type.sectionHead,
                    color = colors.ink,
                )
            }
            if (detail.log.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.log_rest_day),
                        style = IronTheme.type.body,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(detail.log, key = { it.workoutId }) { workout ->
                WorkoutLog(workout, onEdit = { onEdit(workout) }, onDelete = { confirmDelete = workout }, isFirst = workout == detail.log.first())
            }
            if (templates.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 20.dp).onboardingAnchor(OnboardingTarget.LOG_ADD_WORKOUT)) {
                        if (picking) {
                            Text(stringResource(R.string.log_which_workout), style = IronTheme.type.bodyStrong, color = colors.ink)
                            RowGroup {
                                templates.forEach { template ->
                                    RuledRow(onClick = { onAdd(template.id) }) {
                                        Text(templateName(template), style = IronTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
                                        Text("›", style = IronTheme.type.body, color = colors.muted)
                                    }
                                }
                            }
                        } else {
                            SecondaryButton(stringResource(R.string.log_add_workout), onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { workout ->
        IronDialog(
            title = stringResource(
                R.string.log_delete_title,
                workout.templateName?.let { templateName(workout.templateId, it) } ?: stringResource(R.string.log_deleted_program),
            ),
            text = stringResource(R.string.log_delete_text),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = {
                onDelete(workout.workoutId)
                confirmDelete = null
            },
            onDismiss = { confirmDelete = null },
            destructive = true,
        )
    }
}

/** "Upper 1   18:40   +165 XP", then one ruled row per exercise with its sets ("80 × 8"). */
@Composable
private fun WorkoutLog(workout: CompletedWorkoutLog, onEdit: () -> Unit, onDelete: () -> Unit, isFirst: Boolean) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val time = Instant.ofEpochMilli(workout.completedAtMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(workout.templateName?.let { templateName(workout.templateId, it) } ?: stringResource(R.string.log_deleted_program), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
            Text(formatTimeOfDay(time.hour * 60 + time.minute), style = type.secondary, color = colors.muted)
            Text(stringResource(R.string.common_xp_gain, workout.xpEarned), style = type.secondary, color = colors.muted, modifier = Modifier.padding(start = 12.dp))
        }
        RowGroup {
            workout.exercises.forEach { ExerciseRow(it) }
        }
        // Only the first workout's actions are the coach-mark target (one hole per step).
        RowActions(
            listOf(RowAction(stringResource(R.string.common_edit), onEdit), RowAction(stringResource(R.string.common_delete), onDelete, destructive = true)),
            if (isFirst) Modifier.onboardingAnchor(OnboardingTarget.LOG_WORKOUT_ACTIONS) else Modifier,
        )
    }
}

@Composable
private fun ExerciseRow(exercise: ExerciseLog) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val locale = currentLocale()
    RuledRow {
        Column(Modifier.weight(1f)) {
            Text(exerciseName(exercise.exerciseId, exercise.name), style = type.body, color = colors.ink)
            Text(
                exercise.sets.map { set ->
                    when (exercise.trackingType) {
                        TrackingType.WEIGHT_REPS -> stringResource(R.string.log_set_weight_reps, Formats.decimal(set.weightKg, locale), set.reps)
                        TrackingType.DURATION -> stringResource(R.string.unit_min_value, Formats.integer(set.durationMinutes ?: 0, locale))
                    }
                }.joinToString("   "),
                style = type.secondary,
                color = colors.muted,
            )
        }
    }
}
