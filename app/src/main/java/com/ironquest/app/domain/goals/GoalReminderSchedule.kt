package com.ironquest.app.domain.goals

import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime

/** The next daily goal notification of one kind. [targetEpochDay] is the day it is about. */
data class GoalAlarm(val reminder: GoalReminder, val triggerAtMillis: Long, val targetEpochDay: Long) {
    val key: String get() = GoalReminderSchedule.keyOf(reminder)
    val requestCode: Int get() = GoalReminderSchedule.requestCode(reminder)
}

/**
 * Daily goal notifications: workout goals at the workout time, food at
 * the food time. Only the next occurrence is armed; whether to post is decided when it fires.
 */
object GoalReminderSchedule {
    /**
     * Fixed request/notification ids. Negative, so they never meet
     * `ReminderSchedule.requestCode` (`id shl 1 or kind`, non-negative while ids stay below 2^30).
     */
    const val WORKOUT_REQUEST_CODE = -1_001
    const val FOOD_REQUEST_CODE = -1_002

    fun requestCode(reminder: GoalReminder): Int = when (reminder) {
        GoalReminder.WORKOUT -> WORKOUT_REQUEST_CODE
        GoalReminder.FOOD -> FOOD_REQUEST_CODE
    }

    fun keyOf(reminder: GoalReminder): String = "goal:${reminder.name.lowercase()}" // not UI text

    fun compute(settings: GoalSettings, clock: Clock): List<GoalAlarm> = buildList {
        if (settings.notifiedWorkoutTypes.isNotEmpty()) add(next(GoalReminder.WORKOUT, settings.workoutReminderMin, clock))
        if (settings.notifies(GoalType.FOOD)) add(next(GoalReminder.FOOD, settings.foodReminderMin, clock))
    }

    /** Today at [timeOfDayMin] if that's still ahead, otherwise tomorrow. */
    fun next(reminder: GoalReminder, timeOfDayMin: Int, clock: Clock): GoalAlarm {
        val now = clock.instant()
        val today = now.atZone(clock.zone).toLocalDate()
        val minute = timeOfDayMin.coerceIn(0, 1439)
        val time = LocalTime.of(minute / 60, minute % 60)
        var date = today
        var trigger = ZonedDateTime.of(date, time, clock.zone).toInstant()
        if (!trigger.isAfter(now)) {
            date = today.plusDays(1)
            trigger = ZonedDateTime.of(date, time, clock.zone).toInstant()
        }
        return GoalAlarm(reminder, trigger.toEpochMilli(), date.toEpochDay())
    }

    /** Workout notification: only while no workout was completed today and there is a goal to list. */
    fun shouldPostWorkout(completedToday: Boolean, goals: List<WorkoutGoal>): Boolean = !completedToday && goals.isNotEmpty()

    /** Food notification: the shown food targets that are still open. */
    fun openFoodGoals(goals: List<FoodGoal>): List<FoodGoal> = goals.filterNot { it.isDone }
}
