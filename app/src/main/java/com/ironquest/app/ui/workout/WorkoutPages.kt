package com.ironquest.app.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironquest.app.R
import com.ironquest.app.domain.appearance.AppStyle
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.SupersetRounds
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.domain.progression.OverloadSuggestion
import com.ironquest.app.domain.workout.SessionExercise
import com.ironquest.app.domain.workout.SessionRow
import com.ironquest.app.domain.workout.supersetAfter
import com.ironquest.app.domain.workout.supersetCurrent
import com.ironquest.app.ui.catalog.exerciseName
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.kit.AddLink
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme

@Composable
internal fun SinglePage(exercise: SessionExercise, isLast: Boolean, canComplete: Boolean, onAction: (WorkoutAction) -> Unit, onOpenDialog: (DialogTarget) -> Unit, onNextPage: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val row = exercise.currentRow
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        ExerciseTitle(exerciseName(exercise.definition.exercise))
        HowToLink(exercise.definition.exercise, Modifier.onboardingAnchor(OnboardingTarget.WORKOUT_HOW_TO))
        OverloadLine(exercise)
        GoalLine(exercise)
        if (row != null) {
            Text(
                setLine(exercise, row),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(top = 14.dp),
                textAlign = TextAlign.Center,
            )
            NumbersAnchor { Steppers(exercise, row, onAction, onOpenDialog) }
            PrimaryButton(
                text = stringResource(R.string.workout_log_set),
                onClick = { onAction(WorkoutAction.LogSet(exercise.id, row.index)) },
                enabled = !row.isSaving,
                modifier = Modifier.padding(top = 12.dp).onboardingAnchor(OnboardingTarget.WORKOUT_LOG_BUTTON),
            )
        } else {
            Text(stringResource(R.string.workout_all_sets_logged), style = type.body, color = colors.muted, modifier = Modifier.padding(top = 20.dp))
            DoneButton(isLast, canComplete, onAction, onNextPage)
        }
    }
    LoggedSets(listOf(exercise), labels = null, onOpenDialog)
    AddLink(stringResource(R.string.workout_add_set), { onAction(WorkoutAction.AddSet(exercise.id)) })
}

@Composable
internal fun SupersetPage(
    block: WorkoutBlock.Superset,
    group: List<SessionExercise>,
    isLast: Boolean,
    canComplete: Boolean,
    onAction: (WorkoutAction) -> Unit,
    onOpenDialog: (DialogTarget) -> Unit,
    onNextPage: () -> Unit,
) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val current = group.supersetCurrent()
    val rounds = SupersetRounds.rounds(group.map { it.rows.size })
    val round = current?.setIndex?.plus(1) ?: rounds

    Column(Modifier.onboardingAnchor(OnboardingTarget.SUPERSET_ROUNDS)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(IronTheme.style.cased(stringResource(R.string.workout_superset_title, block.letter)), style = type.sectionHead, color = colors.accent)
            Spacer(Modifier.width(8.dp))
            Text(
                if (current == null) pluralStringResource(R.plurals.workout_superset_all_done_count, rounds, rounds) else stringResource(R.string.workout_superset_round, round, rounds),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.weight(1f),
            )
        }
        RoundDots(done = if (current == null) rounds else round - 1, rounds = rounds)
    }

    if (current != null) {
        val exercise = group[current.member]
        val row = exercise.rows[current.setIndex]
        val after = group.supersetAfter(current)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(stringResource(R.string.workout_superset_member_now, supersetMember(block, current.member)), style = type.secondary, color = colors.muted)
            ExerciseTitle(exerciseName(exercise.definition.exercise))
            HowToLink(exercise.definition.exercise, Modifier.onboardingAnchor(OnboardingTarget.WORKOUT_HOW_TO))
            OverloadLine(exercise)
            GoalLine(exercise)
            Text(
                setLine(exercise, row),
                style = type.secondary,
                color = colors.muted,
                modifier = Modifier.padding(top = 10.dp),
                textAlign = TextAlign.Center,
            )
            NumbersAnchor { Steppers(exercise, row, onAction, onOpenDialog) }
        }
        RowGroup(Modifier.padding(top = 8.dp)) {
            group.forEachIndexed { member, partner ->
                if (member != current.member) PartnerLine(supersetMember(block, member), partner, isNext = after?.member == member)
            }
        }
        val nextName = after?.takeIf { it.member != current.member }?.let { exerciseName(group[it.member].definition.exercise) }
        PrimaryButton(
            text = nextName?.let { stringResource(R.string.workout_superset_log_then, it) } ?: stringResource(R.string.workout_log_set),
            onClick = { onAction(WorkoutAction.LogSet(exercise.id, row.index)) },
            enabled = !row.isSaving,
            modifier = Modifier.padding(top = 12.dp).onboardingAnchor(OnboardingTarget.SUPERSET_BUTTON),
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            group.forEachIndexed { member, ex ->
                Text(stringResource(R.string.workout_dot_pair, supersetMember(block, member), exerciseName(ex.definition.exercise)), style = type.body, color = colors.ink)
            }
            DoneButton(isLast, canComplete, onAction, onNextPage)
        }
    }

    LoggedSets(group, labels = group.indices.map { supersetMember(block, it) }, onOpenDialog)
    AddLink(stringResource(R.string.workout_add_round), { onAction(WorkoutAction.AddRound(group.map { it.id })) })
}

/** The −/+ weight and reps steppers, as one coach-mark target. */
@Composable
private fun NumbersAnchor(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().onboardingAnchor(OnboardingTarget.WORKOUT_NUMBERS), horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

/** "Next exercise" once a page is done; on the last page "Finish" instead. */
@Composable
private fun DoneButton(isLast: Boolean, canComplete: Boolean, onAction: (WorkoutAction) -> Unit, onNextPage: () -> Unit) {
    if (isLast) {
        PrimaryButton(stringResource(R.string.workout_finish), onClick = { onAction(WorkoutAction.Finish) }, enabled = canComplete, modifier = Modifier.padding(top = 12.dp))
    } else {
        PrimaryButton(stringResource(R.string.workout_next_exercise), onClick = onNextPage, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun RoundDots(done: Int, rounds: Int) {
    val colors = IronTheme.colors
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rounds) { index ->
            Box(
                Modifier
                    .width(22.dp)
                    .height(4.dp)
                    .clip(IronTheme.shapes.bar)
                    .background(if (index < done) colors.accent else colors.line),
            )
        }
    }
}

/** "A2 · next: Cable row" with its prescription on the right. */
@Composable
private fun PartnerLine(label: String, partner: SessionExercise, isNext: Boolean) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val done = partner.currentRow == null
    val partnerName = exerciseName(partner.definition.exercise)
    RuledRow {
        Text(
            when {
                done -> stringResource(R.string.workout_partner_done, label, partnerName)
                isNext -> stringResource(R.string.workout_partner_next, label, partnerName)
                else -> stringResource(R.string.workout_dot_pair, label, partnerName)
            },
            style = type.body,
            color = if (done) colors.muted else colors.ink,
            modifier = Modifier.weight(1f),
        )
        Text(prescription(partner), style = type.secondary, color = colors.muted)
    }
}

@Composable
private fun ExerciseTitle(name: String) {
    val base = IronTheme.type.pageTitle
    // The page title sizes are for short day names; exercise names need a step down.
    val (size, line) = when (IronTheme.style.style) {
        AppStyle.DIARY -> 26.sp to 32.sp
        AppStyle.BOLD -> 34.sp to 34.sp
        AppStyle.SOFT -> 24.sp to 30.sp
    }
    Text(
        IronTheme.style.cased(name),
        style = base.copy(fontSize = size, lineHeight = line),
        color = IronTheme.colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** "↑ Try 82.5 kg" / "= Same weight, 70 kg" / "↓ Deload to 72.5 kg" / "First time: …", in its meaning colour. */
@Composable
private fun OverloadLine(exercise: SessionExercise) {
    val colors = IronTheme.colors
    val range = rangeText(exercise.definition.repRange)
    val (text, fg, bg) = when (val suggestion = exercise.suggestion) {
        is OverloadSuggestion.LevelUp -> Triple(stringResource(R.string.workout_overload_level_up, weightText(suggestion.suggestedWeightKg)), colors.up, colors.upBg)
        is OverloadSuggestion.Hold -> Triple(stringResource(R.string.workout_overload_hold, weightText(suggestion.weightKg)), colors.hold, colors.holdBg)
        is OverloadSuggestion.Deload -> Triple(stringResource(R.string.workout_overload_deload, weightText(suggestion.suggestedWeightKg)), colors.deload, colors.deloadBg)
        OverloadSuggestion.FirstEncounter -> Triple(stringResource(R.string.workout_overload_first_time, range), colors.muted, Color.Transparent)
        OverloadSuggestion.NotApplicable -> return
    }
    Text(
        text,
        style = IronTheme.type.bodyStrong,
        color = fg,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(top = 6.dp)
            .onboardingAnchor(OnboardingTarget.WORKOUT_OVERLOAD)
            .clip(IronTheme.shapes.chip)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Today's goal for the exercise; turns `reward` with a tick once reached (paid at finish). */
@Composable
private fun GoalLine(exercise: SessionExercise) {
    val goal = exercise.goal ?: return
    val colors = IronTheme.colors
    val label = GoalCopy.label(goal.type).resolve()
    val text = stringResource(if (exercise.goalReached) R.string.workout_goal_line_reached else R.string.workout_goal_line, label, goal.bonusXp)
    val color = when {
        exercise.goalReached || goal.type == GoalType.LIMIT_BREAK -> colors.reward
        else -> colors.accent
    }
    Text(text, style = IronTheme.type.secondary, color = color, modifier = Modifier.padding(top = 6.dp))
}

/** "Set 3 of 4 · last time 80 × 8". */
@Composable
private fun setLine(exercise: SessionExercise, row: SessionRow): String {
    val ghost = row.ghost ?: return stringResource(R.string.workout_set_line, row.index + 1, exercise.rows.size)
    return stringResource(R.string.workout_set_line_ghost, row.index + 1, exercise.rows.size, setText(ghost, exercise.isCardio))
}

/** A partner's next set at a glance: "55 kg × 8–12" / "8–12 reps" / "15–20 min". */
@Composable
private fun prescription(exercise: SessionExercise): String {
    val range = rangeText(exercise.definition.repRange)
    if (exercise.isCardio) return stringResource(R.string.unit_min_value, range)
    val weight = exercise.currentRow?.let { exercise.shownWeight(it) }.orEmpty()
    return if (weight.isEmpty()) stringResource(R.string.workout_prescription_reps, range) else stringResource(R.string.workout_prescription_weight, weightText(weight), range)
}
