package com.ironquest.app.ui.workout

import com.ironquest.app.domain.goals.GoalReached
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.workout.SessionExercise
import com.ironquest.app.domain.workout.WorkoutSession
import com.ironquest.app.ui.text.UiText
import java.time.LocalDate

data class VictoryUi(
    val setXp: Int,
    val bonusXp: Int,
    val totalXp: Int,
    val levelUp: LevelUpEvent?,
    val goals: List<GoalReached> = emptyList(),
    val sets: Int = 0,
    val durationMillis: Long? = null,
) {
    val goalXp: Int get() = goals.sumOf { goal -> goal.xp }
}

data class ActiveWorkoutUiState(
    val isLoading: Boolean = true,
    val templateName: UiText = UiText.Verbatim(""),
    /** Date of the session being replayed as "last time", null on the first run of this workout. */
    val ghostDate: LocalDate? = null,
    val session: WorkoutSession = WorkoutSession(),
    /** Pager pages: single exercises and supersets, in template order. */
    val blocks: List<WorkoutBlock> = emptyList(),
    /** Page to open on: a resumed session continues at its first unfinished block. */
    val initialPage: Int = 0,
    val isCompleting: Boolean = false,
    val victory: VictoryUi? = null,
    /** Bumped by a restart, so the pager starts over on the first page. */
    val sessionKey: Int = 0,
    /** A restart or stop is deleting the session. */
    val isResetting: Boolean = false,
    /** The template was deleted (its imported program is gone); nothing can be logged. */
    val notFound: Boolean = false,
) {
    val exercises: List<SessionExercise> get() = session.exercises
    val loggedSets: Int get() = session.loggedSets
    val targetSets: Int get() = session.targetSets
    val sessionXp: Int get() = session.earnedXp
    val canComplete: Boolean get() = loggedSets > 0 && !isCompleting && !isResetting && victory == null

    fun exercisesOf(block: WorkoutBlock): List<SessionExercise> = session.exercisesOf(block)

    /** What the workout tips need to know on pager page [currentPage]. The finish summary is never covered. */
    fun onboardingFacts(currentPage: Int): Set<OnboardingFact> = buildSet {
        if (victory != null) add(OnboardingFact.BLOCKED)
        if (loggedSets > 0) add(OnboardingFact.SET_LOGGED)
        if (blocks.getOrNull(currentPage) is WorkoutBlock.Superset) add(OnboardingFact.SUPERSET_VISIBLE)
    }
}

sealed interface WorkoutEvent {
    data class LevelUp(val event: LevelUpEvent) : WorkoutEvent

    /** A shown goal was reached live (stamp; its XP is paid at finish). Only sent when Celebrate is on. */
    data class GoalReached(val type: GoalType, val exerciseName: UiText, val bonusXp: Int) : WorkoutEvent
    data class Message(val text: UiText) : WorkoutEvent

    /** The session was stopped and deleted: leave the screen. */
    data object Stopped : WorkoutEvent
}

/** Everything the workout screen can ask the ViewModel to do. */
sealed interface WorkoutAction {
    data class WeightChange(val exerciseId: String, val index: Int, val text: String) : WorkoutAction
    data class AmountChange(val exerciseId: String, val index: Int, val text: String) : WorkoutAction
    data class StepWeight(val exerciseId: String, val index: Int, val up: Boolean) : WorkoutAction
    data class StepAmount(val exerciseId: String, val index: Int, val up: Boolean) : WorkoutAction
    data class LogSet(val exerciseId: String, val index: Int) : WorkoutAction
    data class EditSet(val exerciseId: String, val index: Int, val weightText: String, val amountText: String) : WorkoutAction
    data class DeleteSet(val exerciseId: String, val index: Int) : WorkoutAction
    data class AddSet(val exerciseId: String) : WorkoutAction
    data class AddRound(val exerciseIds: List<String>) : WorkoutAction
    data object Restart : WorkoutAction
    data object Stop : WorkoutAction
    data object Finish : WorkoutAction
}
