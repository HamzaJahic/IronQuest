package com.ironquest.app.domain.goals

/** The three per-type switches on the You tab. */
enum class GoalSwitch { SHOW, CELEBRATE, NOTIFY }

data class GoalTypeSettings(val show: Boolean = true, val celebrate: Boolean = true, val notify: Boolean = true) {
    fun get(switch: GoalSwitch): Boolean = when (switch) {
        GoalSwitch.SHOW -> show
        GoalSwitch.CELEBRATE -> celebrate
        GoalSwitch.NOTIFY -> notify
    }

    fun with(switch: GoalSwitch, on: Boolean): GoalTypeSettings = when (switch) {
        GoalSwitch.SHOW -> copy(show = on)
        GoalSwitch.CELEBRATE -> copy(celebrate = on)
        GoalSwitch.NOTIFY -> copy(notify = on)
    }
}

/** Which goal notification a time belongs to. */
enum class GoalReminder { WORKOUT, FOOD }

data class GoalSettings(
    val byType: Map<GoalType, GoalTypeSettings> = GoalType.entries.associateWith { GoalTypeSettings() },
    /** Minutes after midnight. */
    val workoutReminderMin: Int = DEFAULT_WORKOUT_MIN,
    val foodReminderMin: Int = DEFAULT_FOOD_MIN,
) {
    fun of(type: GoalType): GoalTypeSettings = byType[type] ?: GoalTypeSettings()

    fun isOn(type: GoalType, switch: GoalSwitch): Boolean = of(type).get(switch)

    /** Goal types shown on Today; only these are paid at completion. */
    val shownTypes: Set<GoalType> get() = GoalType.entries.filterTo(mutableSetOf()) { of(it).show }

    /** A goal type notifies only while it is also shown (a hidden type has no goals). */
    fun notifies(type: GoalType): Boolean = of(type).show && of(type).notify

    fun reminderMin(reminder: GoalReminder): Int = when (reminder) {
        GoalReminder.WORKOUT -> workoutReminderMin
        GoalReminder.FOOD -> foodReminderMin
    }

    /** Workout-goal types that feed the morning notification. */
    val notifiedWorkoutTypes: Set<GoalType>
        get() = setOf(GoalType.LIMIT_BREAK, GoalType.BEAT_LAST_TIME).filterTo(mutableSetOf(), ::notifies)

    companion object {
        const val DEFAULT_WORKOUT_MIN = 8 * 60
        const val DEFAULT_FOOD_MIN = 20 * 60
        val Default = GoalSettings()
    }
}
