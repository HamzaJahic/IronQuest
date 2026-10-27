package com.ironquest.app.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.domain.progression.formatKg
import com.ironquest.app.domain.workout.SessionExercise
import com.ironquest.app.domain.workout.SessionRow
import com.ironquest.app.domain.workout.SetInput
import com.ironquest.app.ui.kit.IronDialog
import com.ironquest.app.ui.kit.IronTextField
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme

/** Big weight and reps (or minutes) numbers with −/+; tap a number to type it. */
@Composable
internal fun Steppers(exercise: SessionExercise, row: SessionRow, onAction: (WorkoutAction) -> Unit, onOpenDialog: (DialogTarget) -> Unit) {
    val colors = IronTheme.colors
    if (!exercise.isCardio) {
        val weight = exercise.shownWeight(row)
        StepperRow(
            value = weight.ifEmpty { "—" },
            unit = stringResource(R.string.unit_kg),
            caption = stringResource(R.string.workout_stepper_weight_caption, weightText(exercise.weightStepKg)),
            lessLabel = stringResource(R.string.workout_stepper_less_weight_cd),
            moreLabel = stringResource(R.string.workout_stepper_more_weight_cd),
            onLess = { onAction(WorkoutAction.StepWeight(exercise.id, row.index, up = false)) },
            onMore = { onAction(WorkoutAction.StepWeight(exercise.id, row.index, up = true)) },
            onTap = { onOpenDialog(DialogTarget.Weight(exercise.id, row.index, weight)) },
        )
    }
    val unit = stringResource(if (exercise.isCardio) R.string.unit_min else R.string.workout_unit_reps)
    StepperRow(
        value = row.amountInput.ifEmpty { "—" },
        unit = unit,
        caption = stringResource(R.string.workout_stepper_target_caption, rangeText(exercise.definition.repRange)),
        lessLabel = stringResource(if (exercise.isCardio) R.string.workout_stepper_fewer_minutes_cd else R.string.workout_stepper_fewer_reps_cd),
        moreLabel = stringResource(if (exercise.isCardio) R.string.workout_stepper_more_minutes_cd else R.string.workout_stepper_more_reps_cd),
        onLess = { onAction(WorkoutAction.StepAmount(exercise.id, row.index, up = false)) },
        onMore = { onAction(WorkoutAction.StepAmount(exercise.id, row.index, up = true)) },
        onTap = { onOpenDialog(DialogTarget.Amount(exercise.id, row.index, row.amountInput, exercise.isCardio)) },
    )
    if (exercise.beatsGhost(row)) {
        Text(stringResource(R.string.workout_beats_last_time), style = IronTheme.type.secondary, color = colors.accent)
    }
}

@Composable
private fun StepperRow(
    value: String,
    unit: String,
    caption: String,
    lessLabel: String,
    moreLabel: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", lessLabel, onLess)
        Column(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = stringResource(R.string.workout_stepper_type_cd, unit), role = Role.Button, onClick = onTap)
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = bigNumber(), color = if (value == "—") colors.muted else colors.ink)
                Text(stringResource(R.string.workout_stepper_unit_suffix, unit), style = type.body, color = colors.muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(caption, style = type.secondary, color = colors.muted)
        }
        StepButton("+", moreLabel, onMore)
    }
}

/** The style's number font at stepper size. */
@Composable
private fun bigNumber(): TextStyle {
    val (size, line) = when (IronTheme.style.style) {
        AppStyle.DIARY -> 48.sp to 54.sp
        AppStyle.BOLD -> 56.sp to 58.sp
        AppStyle.SOFT -> 44.sp to 50.sp
    }
    return IronTheme.type.number.copy(fontSize = size, lineHeight = line)
}

/** Diary: outlined circle. Bold: outlined square. Soft: filled `raised` circle. */
@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    val colors = IronTheme.colors
    val style = IronTheme.style.style
    val shape = if (style == AppStyle.BOLD) IronTheme.shapes.button else CircleShape
    Box(
        Modifier
            .size(52.dp)
            .clip(shape)
            .then(
                if (style == AppStyle.SOFT) {
                    Modifier.background(colors.raised)
                } else {
                    Modifier.border(BorderStroke(if (style == AppStyle.BOLD) 2.dp else 1.5.dp, colors.ink), shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // Body font: the number fonts' − and + are hairline in Fraunces.
        Text(symbol, style = IronTheme.type.bodyStrong.copy(fontSize = 26.sp, lineHeight = 28.sp), color = colors.ink)
    }
}

/** Logged sets, newest last: "✓ 82.5 × 7  +15"; tap one to edit it (no XP change) or delete it. */
@Composable
internal fun LoggedSets(group: List<SessionExercise>, labels: List<String>?, onOpenDialog: (DialogTarget) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val logged = group.flatMapIndexed { member, ex -> ex.rows.filter { it.isLogged }.map { Triple(member, ex, it) } }
        // Supersets read in the order they were done: round by round.
        .sortedWith(compareBy({ it.third.index }, { it.first }))
    if (logged.isEmpty()) return
    SectionHeader(stringResource(R.string.workout_logged_header))
    RowGroup(Modifier.onboardingAnchor(OnboardingTarget.WORKOUT_LOGGED_SETS)) {
        logged.forEach { (member, ex, row) ->
            val set = row.persisted ?: return@forEach
            RuledRow(onClick = {
                onOpenDialog(
                    DialogTarget.LoggedSetEdit(
                        exerciseId = ex.id,
                        index = row.index,
                        weight = if (ex.isCardio) "" else set.weightKg.formatKg(),
                        amount = (if (ex.isCardio) set.durationMinutes ?: 0 else set.reps).toString(),
                        cardio = ex.isCardio,
                    ),
                )
            }) {
                Text("✓", style = type.bodyStrong, color = colors.accent)
                val setLabel = stringResource(R.string.workout_logged_set, row.index + 1)
                Text(labels?.let { stringResource(R.string.workout_dot_pair, it[member], setLabel) } ?: setLabel, style = type.secondary, color = colors.muted, modifier = Modifier.width(if (labels != null) 88.dp else 48.dp))
                Text(setText(set, ex.isCardio), style = type.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f))
                row.xp?.let { Text(stringResource(R.string.workout_xp_short, it), style = type.label, color = colors.reward) }
            }
        }
    }
}

@Composable
internal fun NumberDialog(
    target: DialogTarget,
    onDismiss: () -> Unit,
    onSave: (weight: String, amount: String) -> Unit,
    onDelete: () -> Unit,
) {
    val cardio = when (target) {
        is DialogTarget.Weight -> false
        is DialogTarget.Amount -> target.cardio
        is DialogTarget.LoggedSetEdit -> target.cardio
    }
    val showWeight = target is DialogTarget.Weight || (target is DialogTarget.LoggedSetEdit && !cardio)
    val showAmount = target !is DialogTarget.Weight

    // Pre-selected, so typing replaces the shown value instead of appending to it.
    fun selected(text: String) = TextFieldValue(text, TextRange(0, text.length))
    var weightField by remember(target) {
        mutableStateOf(
            selected(
                when (target) {
                    is DialogTarget.Weight -> target.initial
                    is DialogTarget.LoggedSetEdit -> target.weight
                    else -> ""
                },
            ),
        )
    }
    var amountField by remember(target) {
        mutableStateOf(
            selected(
                when (target) {
                    is DialogTarget.Amount -> target.initial
                    is DialogTarget.LoggedSetEdit -> target.amount
                    else -> ""
                },
            ),
        )
    }
    val weight = weightField.text
    val amount = amountField.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(target) { focus.requestFocus() }
    val weightOk = !showWeight || weight.replace(',', '.').toDoubleOrNull()?.let { it in 0.0..SetInput.MAX_WEIGHT_KG } == true
    val amountOk = !showAmount || amount.toIntOrNull()?.let { it in 1..SetInput.MAX_AMOUNT } == true
    val amountLabel = stringResource(if (cardio) R.string.workout_dialog_minutes else R.string.workout_dialog_reps)
    IronDialog(
        title = when (target) {
            is DialogTarget.Weight -> stringResource(R.string.workout_dialog_weight_title)
            is DialogTarget.Amount -> amountLabel
            is DialogTarget.LoggedSetEdit -> stringResource(R.string.workout_dialog_edit_set_title, target.index + 1)
        },
        confirmLabel = stringResource(R.string.common_save),
        onConfirm = { onSave(weight, amount) },
        confirmEnabled = weightOk && amountOk,
        onDismiss = onDismiss,
        onDelete = if (target is DialogTarget.LoggedSetEdit) onDelete else null,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showWeight) {
                IronTextField(
                    stringResource(R.string.workout_dialog_weight_label),
                    weightField,
                    { value -> weightField = value.copy(text = value.text.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(7)) },
                    modifier = Modifier.focusRequester(focus),
                    isError = !weightOk,
                    keyboardType = KeyboardType.Decimal,
                )
            }
            if (showAmount) {
                IronTextField(
                    amountLabel,
                    amountField,
                    { value -> amountField = value.copy(text = value.text.filter(Char::isDigit).take(3)) },
                    modifier = if (showWeight) Modifier else Modifier.focusRequester(focus),
                    isError = !amountOk,
                    keyboardType = KeyboardType.Number,
                )
            }
        }
    }
}
