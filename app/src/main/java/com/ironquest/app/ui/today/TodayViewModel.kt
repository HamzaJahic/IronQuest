package com.ironquest.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.goals.FoodGoal
import com.ironquest.app.domain.goals.Goal
import com.ironquest.app.domain.goals.GoalRules
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.goals.LimitBreakGoal
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.LastCompletion
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.DaySummaryCalculator
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroLine
import com.ironquest.app.domain.nutrition.NextMeal
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.program.NextWorkout
import com.ironquest.app.domain.program.NextWorkoutChoice
import com.ironquest.app.domain.progression.OverloadSuggestion
import com.ironquest.app.domain.progression.ProgressiveOverloadRules
import com.ironquest.app.domain.progression.WeekStreak
import com.ironquest.app.domain.progression.formatKg
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.math.roundToInt

/** One program day on Today: the main workout or an "Other days" row. */
data class DayUi(
    val template: WorkoutTemplate,
    val lastCompleted: LocalDate?,
    val isInProgress: Boolean,
)

/** One exercise in "The workout" line; [limitBreak] draws the ↑; [superset] is its group letter. */
data class ExerciseLineUi(val id: String, val name: UiText, val limitBreak: Boolean = false, val superset: String? = null)

data class GoalUi(
    val type: GoalType,
    val title: UiText,
    val detail: UiText,
    /** "+25 XP", or "Done" for a reached food target. */
    val hint: UiText,
    val done: Boolean = false,
)

/** Today's food line: protein bar + next meal → Food tab. */
data class FoodLineUi(
    val planName: String?,
    val protein: MacroLine,
    /** See [NextMeal.pick]. */
    val nextMeal: PlanSlot?,
    /** Active plan with ≥ 1 slot, all of them logged today. */
    val allDone: Boolean,
    /** Kcal eaten today when the mode has no plan ("Log as I go"); null when the plan drives the line. */
    val loggedKcal: Double? = null,
) {
    val nextLine: NextLine
        get() = when {
            loggedKcal != null -> NextLine.Logged(loggedKcal.roundToInt(), protein.eaten.roundToInt(), protein.target)
            planName == null -> NextLine.NoPlan
            allDone -> NextLine.AllDone
            nextMeal != null -> NextLine.Next(nextMeal.label, nextMeal.timeOfDayMin)
            else -> NextLine.NoMeals
        }
}

/** What the food line says about the next meal; the screen turns it into text (the time follows the device's 12/24-hour setting). */
sealed interface NextLine {
    data object NoPlan : NextLine
    data object AllDone : NextLine
    data object NoMeals : NextLine
    data class Logged(val kcal: Int, val proteinG: Int, val proteinTargetG: Double?) : NextLine
    data class Next(val label: String, val timeOfDayMin: Int?) : NextLine
}

data class TodayUiState(
    val isLoading: Boolean = true,
    /** No active program (the built-in one was deleted) and no unfinished workout: Today shows a hint. */
    val noProgram: Boolean = false,
    val date: LocalDate = LocalDate.MIN,
    val level: Int = 1,
    /** The unfinished day, else the "Do next" choice, else the rotation's next day. */
    val main: DayUi? = null,
    /** One line about the main lift ("Bench press 82.5 kg, up 2.5 from last time"). */
    val mainNote: UiText = UiText.Verbatim(""),
    val exercises: List<ExerciseLineUi> = emptyList(),
    /** Only goal types the user shows; empty hides the section. */
    val goals: List<GoalUi> = emptyList(),
    val otherDays: List<DayUi> = emptyList(),
    /** The main day is the user's "Do next" choice: shows "Back to the rotation". */
    val mainIsChosen: Boolean = false,
    /** What the rotation alone would pick; "Do next" on that day clears the choice instead of storing it. */
    val rotationTemplateId: String? = null,
    val weekDone: Int = 0,
    val weekTarget: Int = 0,
    val streakWeeks: Int = 0,
    /** Null until nutrition loads, or if it fails: the food line and protein cell then hide. */
    val food: FoodLineUi? = null,
)

/** What Today's tips need to know: the goals tip waits for goals, the "other days" step for other days, the "Do next" step for its action. */
fun TodayUiState.onboardingFacts(): Set<OnboardingFact> = buildSet {
    if (goals.isNotEmpty()) add(OnboardingFact.GOALS_VISIBLE)
    if (otherDays.isNotEmpty()) add(OnboardingFact.OTHER_DAYS_VISIBLE)
    if (otherDays.isNotEmpty() && main?.isInProgress == false) add(OnboardingFact.DO_NEXT_VISIBLE)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    repository: WorkoutRepository,
    nutrition: NutritionRepository,
    private val clock: Clock,
    programs: ProgramRepository,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))

    /** Called on resume so Today rolls over at midnight (the ViewModel outlives the day). */
    fun refreshToday() {
        today.value = LocalDate.now(clock)
    }

    /** "Do next" on an Other days row. The rotation's own day clears the choice, so nothing stale stays stored. */
    fun doNext(templateId: String) {
        val choice = NextWorkoutChoice(templateId, clock.millis()).takeIf { templateId != uiState.value.rotationTemplateId }
        settings.setNextWorkoutChoice(choice)
    }

    fun backToRotation() = settings.setNextWorkoutChoice(null)

    /**
     * The active program's days — plus an unfinished session's day when it belongs to another
     * program (switched mid-workout), so resuming stays one tap.
     */
    private val board: Flow<Pair<Program?, List<WorkoutTemplate>>> = combine(
        programs.observeActiveProgram(),
        repository.observeInProgressTemplateId(),
    ) { program, inProgressId -> program to inProgressId }
        .mapLatest { (program, inProgressId) ->
            val extra = inProgressId
                ?.takeIf { id -> program?.templates?.none { it.id == id } != false }
                ?.let { programs.findTemplate(it) }
            program to (program?.templates.orEmpty() + listOfNotNull(extra))
        }
        .distinctUntilChanged()

    /** Each day with its ghost (last completed session). */
    private val ghosts: Flow<Pair<Program?, List<Pair<WorkoutTemplate, GhostSession?>>>> = board
        .flatMapLatest { (program, templates) ->
            // combine() of no flows never emits, so an empty board is handled separately.
            if (templates.isEmpty()) {
                flowOf(program to emptyList())
            } else {
                combine(templates.map { template -> repository.observeGhost(template.id).map { template to it } }) { ghosts -> program to ghosts.toList() }
            }
        }

    /** Completed-workout dates from the streak window's start to the end of this week. */
    private val completedDates: Flow<Pair<LocalDate, List<LocalDate>>> = today
        .flatMapLatest { day ->
            val monday = WeekStreak.mondayOf(day)
            repository.observeCompletedDates(WeekStreak.windowStart(day), monday.plusDays(6)).map { day to it }
        }

    /** What decides the main day besides the program: last completion, the unfinished day, the "Do next" choice. */
    private val pickInputs: Flow<Triple<LastCompletion?, String?, NextWorkoutChoice?>> = combine(
        repository.observeLastCompletion(),
        repository.observeInProgressTemplateId(),
        settings.observeNextWorkoutChoice(),
    ) { last, inProgressId, choice -> Triple(last, inProgressId, choice) }

    /** Today without goals and food, plus the main workout's goals (filtered by settings later). */
    private val workoutState: Flow<Pair<TodayUiState, List<Goal>>> = combine(
        repository.observeProfile(),
        ghosts,
        pickInputs,
        completedDates,
    ) { profile, (program, days), (last, inProgressId, choice), (day, dates) ->
        if (days.isEmpty()) {
            return@combine TodayUiState(isLoading = false, noProgram = true, date = day, level = profile.level.level) to emptyList()
        }
        val cards = days.map { (template, ghost) -> DayUi(template, ghost?.date, isInProgress = template.id == inProgressId) }
        // An unfinished workout always takes the main slot so it's one tap to resume (unless its
        // imported day was deleted, then the choice or the rotation decides).
        val resumable = inProgressId?.takeIf { id -> cards.any { it.template.id == id } }
        val pick = NextWorkout.pick(program, last?.templateId, last?.completedAtMillis, resumable, choice)
        val mainId = pick.templateId ?: cards.first().template.id
        val main = cards.first { it.template.id == mainId }
        val ghost = days.first { it.first.id == mainId }.second
        val monday = WeekStreak.mondayOf(day)
        TodayUiState(
            isLoading = false,
            date = day,
            level = profile.level.level,
            main = main,
            mainNote = mainNote(main.template, ghost),
            otherDays = cards.filter { it.template.id != mainId },
            mainIsChosen = pick.chosen,
            rotationTemplateId = pick.rotationTemplateId,
            weekDone = dates.count { !it.isBefore(monday) },
            weekTarget = program?.templates?.size ?: 0,
            streakWeeks = WeekStreak.compute(dates, program?.templates?.size ?: 0, day),
            exercises = main.template.blocks.flatMap { block ->
                val letter = (block as? WorkoutBlock.Superset)?.letter
                block.exercises.map { ExerciseLineUi(it.exercise.id, exerciseNameText(it.exercise), superset = letter) }
            },
        ) to GoalRules.forWorkout(main.template, ghost?.setsByExercise.orEmpty())
    }

    /**
     * "Now" is read on each emission (no ticker); see CLAUDE.md "Behaviors". Nutrition is optional
     * here: until it loads, or if it fails, the food line and goals are simply hidden.
     */
    private val foodDay: StateFlow<DayNutrition?> = today
        .flatMapLatest { day ->
            nutrition.observeDay(day)
                .map<DayNutrition, DayNutrition?> { it }
                .catch { emit(null) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Goal switches and the food tracking mode change together rarely; one flow keeps `combine` within its typed overloads. */
    private val settingsFlow = combine(settings.observeGoals(), settings.observeFood().map { it.mode }.distinctUntilChanged()) { goals, mode -> goals to mode }

    val uiState: StateFlow<TodayUiState> = combine(workoutState, settingsFlow, foodDay) { (state, workoutGoals), (prefs, mode), day ->
        val now = LocalTime.now(clock)
        val food = day?.let { buildFoodLine(it, now.hour * 60 + now.minute, mode) }
        val foodGoals = day?.let { GoalRules.forFood(it) }.orEmpty()
        val shown = (workoutGoals + foodGoals).filter { prefs.of(it.type).show }
        val limitBreaks = shown.filterIsInstance<LimitBreakGoal>().mapTo(mutableSetOf()) { it.exercise.exercise.id }
        state.copy(
            goals = shown.map(::goalUi),
            exercises = state.exercises.map { it.copy(limitBreak = it.id in limitBreaks) },
            food = food,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodayUiState(),
    )

    private fun mainNote(template: WorkoutTemplate, ghost: GhostSession?): UiText {
        val main = template.mainLift
        val name = exerciseNameText(main.exercise)
        return when (val suggestion = ProgressiveOverloadRules.suggest(main, ghost?.setsByExercise?.get(main.exercise.id).orEmpty())) {
            is OverloadSuggestion.LevelUp ->
                uiText(R.string.today_note_level_up, name, suggestion.suggestedWeightKg.formatKg(), suggestion.incrementKg.formatKg())
            is OverloadSuggestion.Hold -> uiText(R.string.today_note_hold, name, suggestion.weightKg.formatKg())
            is OverloadSuggestion.Deload ->
                uiText(R.string.today_note_deload, name, suggestion.suggestedWeightKg.formatKg(), suggestion.previousWeightKg.formatKg())
            OverloadSuggestion.FirstEncounter -> uiText(R.string.today_note_first_time)
            OverloadSuggestion.NotApplicable -> uiText(R.string.today_note_cardio, name, main.targetSets, uiText(R.string.common_range, main.repRange.min, main.repRange.max))
        }
    }

    companion object {
        fun goalUi(goal: Goal): GoalUi {
            val done = goal is FoodGoal && goal.isDone
            val xp = if (goal is FoodGoal) goal.nutritionBonusXp else goal.bonusXp
            return GoalUi(goal.type, GoalCopy.title(goal), GoalCopy.detail(goal), if (done) uiText(R.string.today_goal_done) else uiText(R.string.common_xp_gain, xp), done)
        }

        /** Pure so the food-line rules can be unit-tested without the workout flows. */
        fun buildFoodLine(day: DayNutrition, nowMin: Int, mode: FoodTrackingMode): FoodLineUi {
            val protein = DaySummaryCalculator.summarize(day.logs, day.targets).protein
            if (!mode.showsPlan) {
                return FoodLineUi(planName = null, protein = protein, nextMeal = null, allDone = false, loggedKcal = day.logs.sumOf { it.eaten.effectiveKcal })
            }
            val plan = day.activePlan
            val next = NextMeal.pick(plan, day.logs, nowMin)
            return FoodLineUi(
                planName = plan?.name,
                protein = protein,
                nextMeal = next,
                allDone = plan != null && plan.slots.isNotEmpty() && next == null,
            )
        }
    }
}
