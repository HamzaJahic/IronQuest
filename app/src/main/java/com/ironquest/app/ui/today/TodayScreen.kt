package com.ironquest.app.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.onboarding.OnboardingScreen
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.catalog.templateName
import com.ironquest.app.ui.format.DatePatterns
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatDate
import com.ironquest.app.ui.format.formatTimeOfDay
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.kit.ActionTone
import com.ironquest.app.ui.kit.BulletList
import com.ironquest.app.ui.kit.LevelMark
import com.ironquest.app.ui.kit.PageTitle
import com.ironquest.app.ui.kit.PrimaryButton
import com.ironquest.app.ui.kit.ProgressLine
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.SummaryCell
import com.ironquest.app.ui.kit.SummaryStrip
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.onboarding.OnboardingGate
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.LevelPlacement
import kotlin.math.roundToInt

@Composable
fun TodayScreen(
    onStartWorkout: (templateId: String) -> Unit,
    onOpenFood: () -> Unit,
    contentPadding: PaddingValues,
    onOpenPrograms: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    // The tips talk about the workout, so they wait for a loaded day (not the "no program" hint).
    if (!state.isLoading && state.main != null) {
        OnboardingGate(OnboardingScreen.TODAY, state.onboardingFacts())
    }
    TodayContent(
        state = state,
        onStartWorkout = onStartWorkout,
        onOpenFood = onOpenFood,
        onDoNext = viewModel::doNext,
        onBackToRotation = viewModel::backToRotation,
        contentPadding = contentPadding,
        onOpenPrograms = onOpenPrograms,
    )
}

@Composable
fun TodayContent(
    state: TodayUiState,
    onStartWorkout: (String) -> Unit,
    onOpenFood: () -> Unit,
    contentPadding: PaddingValues,
    onOpenPrograms: () -> Unit = {},
    onDoNext: (String) -> Unit = {},
    onBackToRotation: () -> Unit = {},
) {
    val main = state.main
    // Loading is quick (local DB): draw nothing rather than a spinner flash.
    if (state.isLoading) return
    if (state.noProgram) {
        NoProgram(onOpenPrograms, contentPadding)
        return
    }
    if (main == null) return
    val colors = IronTheme.colors
    val type = IronTheme.type
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        TodayHeader(state)
        Text(
            state.mainNote.resolve(),
            style = type.body,
            color = colors.ink,
            modifier = Modifier.padding(top = 6.dp),
        )

        SummaryStrip(
            listOf(
                SummaryCell(stringResource(R.string.today_summary_this_week), stringResource(R.string.today_summary_week_progress, state.weekDone, state.weekTarget)),
                SummaryCell(stringResource(R.string.today_summary_streak), stringResource(R.string.today_summary_streak_value, state.streakWeeks)),
                SummaryCell(
                    stringResource(R.string.today_summary_protein),
                    state.food?.let { stringResource(R.string.unit_g_value, it.protein.eaten.roundToInt()) } ?: stringResource(R.string.common_no_value),
                ),
            ),
            Modifier.padding(top = 12.dp).onboardingAnchor(OnboardingTarget.TODAY_SUMMARY),
        )

        if (state.goals.isNotEmpty()) {
            SectionHeader(stringResource(R.string.today_goals_title))
            RowGroup(Modifier.onboardingAnchor(OnboardingTarget.TODAY_GOALS)) {
                state.goals.forEach { goal -> GoalRow(goal) }
            }
        }

        SectionHeader(stringResource(R.string.today_workout_title))
        TodayWorkoutLine(state.exercises)
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(if (main.isInProgress) R.string.today_workout_resume else R.string.today_workout_start),
            onClick = { onStartWorkout(main.template.id) },
            modifier = Modifier.onboardingAnchor(OnboardingTarget.TODAY_START),
        )
        if (state.mainIsChosen) {
            TextAction(stringResource(R.string.today_back_to_rotation), onClick = onBackToRotation, tone = ActionTone.MUTED)
        }

        if (state.otherDays.isNotEmpty()) {
            SectionHeader(stringResource(R.string.today_other_days_title))
            RowGroup(Modifier.onboardingAnchor(OnboardingTarget.TODAY_OTHER_DAYS)) {
                state.otherDays.forEachIndexed { index, day ->
                    RuledRow(onClick = { onStartWorkout(day.template.id) }) {
                        Column(Modifier.weight(1f)) {
                            Text(templateName(day.template), style = type.body, color = colors.ink)
                            Text(otherDayLine(day), style = type.secondary, color = colors.muted)
                        }
                        // Not while a workout is unfinished: finishing it would end the choice at once.
                        if (!main.isInProgress) {
                            TextAction(
                                stringResource(R.string.today_other_day_do_next),
                                onClick = { onDoNext(day.template.id) },
                                modifier = if (index == 0) Modifier.onboardingAnchor(OnboardingTarget.TODAY_DO_NEXT) else Modifier,
                                tone = ActionTone.MUTED,
                            )
                        }
                        Text("›", style = type.body, color = colors.muted)
                    }
                }
            }
        }

        state.food?.let { FoodLine(it, onOpenFood) }
    }
}

/** Date, day name and level mark, laid out per style; also drawn by the appearance preview in setup. */
@Composable
internal fun TodayHeader(state: TodayUiState) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val main = state.main ?: return
    val name = templateName(main.template)
    when (IronTheme.style.levelPlacement) {
        LevelPlacement.CORNER -> Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Formats.date(state.date, stringResource(R.string.today_date_long_pattern), currentLocale()), style = type.secondary, color = colors.muted)
                PageTitle(name)
            }
            LevelMark(state.level)
        }
        LevelPlacement.TOP_BAR -> {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(IronTheme.style.cased(Formats.date(state.date, stringResource(R.string.today_date_short_pattern), currentLocale())), style = type.label, color = colors.ink, modifier = Modifier.weight(1f))
                LevelMark(state.level)
            }
            PageTitle(name)
        }
        LevelPlacement.PILL -> {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(state.date, DatePatterns.WEEKDAY), style = type.secondary, color = colors.muted, modifier = Modifier.weight(1f))
                LevelMark(state.level)
            }
            // Soft is conversational.
            PageTitle(stringResource(if (main.isInProgress) R.string.today_title_resume else R.string.today_title_start, name))
        }
    }
}

@Composable
private fun GoalRow(goal: GoalUi) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    RuledRow {
        Column(Modifier.weight(1f)) {
            Text(IronTheme.style.cased(GoalCopy.label(goal.type).resolve()), style = type.label, color = goalColor(goal.type))
            Text(goal.title.resolve(), style = type.bodyStrong, color = colors.ink)
            Text(goal.detail.resolve(), style = type.secondary, color = colors.muted)
        }
        Text(goal.hint.resolve(), style = type.secondary, color = if (goal.done) colors.reward else colors.muted)
    }
}

/**
 * Meaning colours: Limit break = reward, Beat last time = accent (`up` is meant for `upBg` and
 * vanishes on Highlighter dark's paper), Food = stamina blue.
 */
@Composable
private fun goalColor(type: GoalType): Color = when (type) {
    GoalType.LIMIT_BREAK -> IronTheme.colors.reward
    GoalType.BEAT_LAST_TIME -> IronTheme.colors.accent
    GoalType.FOOD -> IronTheme.colors.sta
}

@Composable
internal fun TodayWorkoutLine(exercises: List<ExerciseLineUi>) {
    val colors = IronTheme.colors
    val supersetJoin = stringResource(R.string.today_workout_superset_join)
    val limitBreakMark = stringResource(R.string.today_workout_limit_break_mark)
    // One bullet per exercise; a superset is one bullet: "A: Incline press + Cable row".
    val groups = mutableListOf<MutableList<ExerciseLineUi>>()
    exercises.forEach { ex ->
        val last = groups.lastOrNull()
        if (ex.superset != null && last != null && last.first().superset == ex.superset) last += ex else groups += mutableListOf(ex)
    }
    val items = groups.map { group ->
        val names = group.map { it.name.resolve() }
        val prefix = group.first().superset?.let { stringResource(R.string.today_workout_superset_prefix, it) }
        buildAnnotatedString {
            prefix?.let { append(it) }
            group.forEachIndexed { i, ex ->
                if (i > 0) append(supersetJoin)
                append(names[i])
                if (ex.limitBreak) withStyle(SpanStyle(color = colors.reward)) { append(limitBreakMark) }
            }
        }
    }
    BulletList(items)
}

@Composable
private fun FoodLine(food: FoodLineUi, onOpen: () -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    SectionHeader(stringResource(R.string.today_food_title))
    Column(
        Modifier
            .fillMaxWidth()
            .onboardingAnchor(OnboardingTarget.TODAY_FOOD_LINE)
            .clickable(onClickLabel = stringResource(R.string.today_food_open_label), role = Role.Button, onClick = onOpen)
            .padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.today_food_protein), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            val eaten = food.protein.eaten.roundToInt()
            Text(
                food.protein.target?.let { stringResource(R.string.today_food_protein_progress, eaten, Formats.decimal(it, currentLocale())) }
                    ?: stringResource(R.string.unit_g_value, eaten),
                style = type.secondary,
                color = colors.muted,
            )
        }
        if (food.protein.target != null) ProgressLine(food.protein.fraction, colors.accent, Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(food.nextLine.text(), style = type.secondary, color = colors.muted, modifier = Modifier.weight(1f))
            Text("›", style = type.body, color = colors.muted)
        }
    }
}

@Composable
private fun NextLine.text(): String = when (this) {
    NextLine.NoPlan -> stringResource(R.string.today_food_no_plan)
    NextLine.AllDone -> stringResource(R.string.today_food_all_done)
    NextLine.NoMeals -> stringResource(R.string.today_food_none_planned)
    is NextLine.Logged -> when {
        // The protein row above already shows protein against its target.
        kcal <= 0 -> stringResource(R.string.today_food_nothing_logged)
        else -> stringResource(R.string.today_food_logged, Formats.integer(kcal, currentLocale()))
    }
    is NextLine.Next ->
        if (timeOfDayMin == null) {
            stringResource(R.string.today_food_next, label)
        } else {
            stringResource(R.string.today_food_next_timed, label, formatTimeOfDay(timeOfDayMin))
        }
}

@Composable
private fun otherDayLine(day: DayUi): String {
    val exercises = pluralStringResource(R.plurals.today_other_day_exercise_count, day.template.exercises.size, day.template.exercises.size)
    val suffix = when {
        day.isInProgress -> stringResource(R.string.today_other_day_in_progress)
        day.lastCompleted != null -> stringResource(
            R.string.today_other_day_last,
            Formats.date(day.lastCompleted, stringResource(R.string.today_date_short_pattern), currentLocale()),
        )
        else -> return exercises
    }
    return exercises + stringResource(R.string.common_dot_separator) + suffix
}

/** Shown when the built-in program was deleted and no other one is active. */
@Composable
private fun NoProgram(onOpenPrograms: () -> Unit, contentPadding: PaddingValues) {
    val colors = IronTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        PageTitle(stringResource(R.string.today_title))
        Text(
            stringResource(R.string.today_no_program_text),
            style = IronTheme.type.body,
            color = colors.muted,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        PrimaryButton(stringResource(R.string.today_no_program_open), onClick = onOpenPrograms)
    }
}
