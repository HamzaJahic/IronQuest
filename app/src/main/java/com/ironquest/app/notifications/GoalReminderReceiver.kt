package com.ironquest.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironquest.app.R
import com.ironquest.app.di.ApplicationScope
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalReminderSchedule
import com.ironquest.app.domain.goals.GoalRules
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.goals.WorkoutGoal
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.program.NextWorkout
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import com.ironquest.app.ui.catalog.templateNameText
import com.ironquest.app.ui.goals.GoalCopy
import com.ironquest.app.ui.navigation.Routes
import com.ironquest.app.ui.text.resolve
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * A daily goal alarm fired: decide whether there is anything to say (same rules as Today), post
 * it, then always re-arm the next occurrence.
 */
@AndroidEntryPoint
class GoalReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var settings: AppSettingsRepository

    @Inject lateinit var workouts: WorkoutRepository

    @Inject lateinit var programs: ProgramRepository

    @Inject lateinit var nutrition: NutritionRepository

    @Inject lateinit var notifier: GoalNotifier

    @Inject lateinit var coordinator: ReminderCoordinator

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_GOAL_FIRE) return
        val reminder = ReminderIntents.goalReminder(intent) ?: return
        goAsyncIn(scope) {
            coordinator.markFired(GoalReminderSchedule.keyOf(reminder))
            try {
                when (reminder) {
                    GoalReminder.WORKOUT -> postWorkoutGoals(context)
                    GoalReminder.FOOD -> postFoodGoals(context)
                }
            } finally {
                coordinator.rearmNow()
            }
        }
    }

    private suspend fun postWorkoutGoals(context: Context) {
        val types = settings.currentGoals().notifiedWorkoutTypes
        if (types.isEmpty()) return
        val today = LocalDate.now(clock)
        val completedToday = workouts.observeCompletedCount(today, today).first() > 0
        val template = todaysTemplate() ?: return
        val ghost = workouts.observeGhost(template.id).first()
        val list: List<WorkoutGoal> = GoalRules.forWorkout(template, ghost?.setsByExercise.orEmpty()).filter { it.type in types }
        if (!GoalReminderSchedule.shouldPostWorkout(completedToday, list)) return
        notifier.show(GoalReminder.WORKOUT, uiText(R.string.notification_goal_workout_title, templateNameText(template)).resolve(context.resources), GoalCopy.workoutNotificationBody(list).resolve(context.resources), Routes.TODAY)
    }

    /** Same pick as Today: an unfinished workout wins, then the "Do next" choice, then the rotation. */
    private suspend fun todaysTemplate(): WorkoutTemplate? {
        val inProgress = workouts.observeInProgressTemplateId().first()?.let { programs.findTemplate(it) }
        if (inProgress != null) return inProgress
        val program = programs.observeActiveProgram().first()
        val last = workouts.observeLastCompletion().first()
        val pick = NextWorkout.pick(program, last?.templateId, last?.completedAtMillis, inProgressTemplateId = null, choice = settings.currentNextWorkoutChoice())
        return program?.templates?.firstOrNull { it.id == pick.templateId }
    }

    private suspend fun postFoodGoals(context: Context) {
        if (!settings.currentGoals().notifies(GoalType.FOOD)) return
        val day = nutrition.observeDay(LocalDate.now(clock)).first()
        val open = GoalReminderSchedule.openFoodGoals(GoalRules.forFood(day))
        if (open.isEmpty()) return
        notifier.show(GoalReminder.FOOD, GoalCopy.foodNotificationTitle(open).resolve(context.resources), GoalCopy.foodNotificationBody(open).resolve(context.resources), Routes.FOOD)
    }
}
