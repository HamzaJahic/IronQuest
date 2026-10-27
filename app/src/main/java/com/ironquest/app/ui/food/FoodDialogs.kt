package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.ChoiceChips
import com.ironquest.app.ui.kit.DialogButton
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronSheet
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SecondaryButton
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.kit.TimeDialog
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Jump to any earlier day to log or fix meals there; days after today can't be picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(current: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val colors = IronTheme.colors
    fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.utcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.utcMillis()
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.raised,
        titleContentColor = colors.muted,
        headlineContentColor = colors.ink,
        weekdayContentColor = colors.muted,
        dayContentColor = colors.ink,
        selectedDayContainerColor = colors.accent,
        selectedDayContentColor = colors.onAccent,
        todayContentColor = colors.ink,
        todayDateBorderColor = colors.ink,
        disabledDayContentColor = colors.line,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = pickerColors,
        confirmButton = {
            DialogButton(
                stringResource(R.string.food_day_picker_confirm),
                onClick = { state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } },
                enabled = state.selectedDateMillis != null,
            )
        },
        dismissButton = { DialogButton(stringResource(R.string.common_cancel), onDismiss, muted = true) },
    ) {
        DatePicker(state = state, colors = pickerColors)
    }
}

@Composable
internal fun SupplementDialog(
    initial: SupplementStatus?,
    onSave: (String, String, Int?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val colors = IronTheme.colors
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var dose by rememberSaveable { mutableStateOf(initial?.dose.orEmpty()) }
    var reminder by rememberSaveable { mutableStateOf(initial?.reminderTimeMin) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    IronDialog(
        title = stringResource(if (initial == null) R.string.food_supplement_new_title else R.string.food_supplement_edit_title),
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = { onSave(name, dose, reminder) },
        confirmEnabled = name.isNotBlank(),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IronTextField(stringResource(R.string.food_supplement_name_hint), name, { name = it })
            IronTextField(stringResource(R.string.food_supplement_dose_hint), dose, { dose = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(
                    reminder?.let { stringResource(R.string.food_supplement_remind_at, formatTimeOfDay(it)) } ?: stringResource(R.string.food_supplement_add_reminder),
                    onClick = { pickingTime = true },
                )
                if (reminder != null) {
                    TextAction(stringResource(R.string.food_supplement_clear_reminder), { reminder = null }, tone = ActionTone.MUTED)
                }
            }
            ReminderPermissionHints(active = reminder != null)
            if (onDelete != null) {
                TextAction(stringResource(R.string.food_supplement_delete), onDelete, tone = ActionTone.DANGER)
            }
        }
    }
    if (pickingTime) {
        TimeDialog(
            initialMin = reminder ?: (8 * 60),
            onConfirm = {
                reminder = it
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

private val PORTION_PRESETS = listOf(0.5, 1.0, 1.5, 2.0)

@Composable
fun PortionDialog(initial: Double, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial.formatAmount()) }
    val locale = currentLocale()
    val presetLabels = PORTION_PRESETS.associateWith { stringResource(R.string.food_portion_value, it.amountText(locale)) }
    IronDialog(
        title = stringResource(R.string.food_portion_title),
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = { onConfirm(text) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceChips(
                options = PORTION_PRESETS,
                selected = PORTION_PRESETS.firstOrNull { it.formatAmount() == text },
                label = { preset -> presetLabels[preset].orEmpty() },
                onSelect = { preset -> preset?.let { text = it.formatAmount() } },
            )
            IronTextField(stringResource(R.string.food_portion_custom_hint), text, { text = it }, keyboardType = KeyboardType.Decimal)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPickerSheet(meals: List<Meal>, onPick: (Meal) -> Unit, onDismiss: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf(false) }
    val filtered = MealLibraryViewModel.filterMeals(meals, query, tag = null)
    IronSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.food_picker_title), style = type.sectionHead, color = colors.ink)
            IronTextField(stringResource(R.string.food_picker_search_hint), query, { query = it }, modifier = Modifier.padding(vertical = 8.dp))
            if (filtered.isEmpty()) {
                Text(
                    stringResource(if (meals.isEmpty()) R.string.food_picker_empty_library else R.string.food_picker_no_match),
                    style = type.body,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(filtered, key = { it.id }) { meal ->
                    RuledRow(onClick = {
                        // Animate the sheet away first, then report the pick.
                        if (!picked) {
                            picked = true
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(meal) }
                        }
                    }) {
                        Text(meal.name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        Text(macroLine(meal.macros), style = type.secondary, color = colors.muted)
                    }
                }
            }
        }
    }
}
