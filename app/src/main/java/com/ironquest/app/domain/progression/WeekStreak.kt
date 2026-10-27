package com.ironquest.app.domain.progression

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Training streak in weeks: consecutive Monday–Sunday weeks in which the number of completed
 * workouts reached the active program's day count. The current week counts once it is complete
 * and never breaks the streak while it is still running.
 */
object WeekStreak {
    /** Full weeks looked back before the current one (two years). */
    const val LOOK_BACK_WEEKS = 104L

    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** First day whose workouts [compute] needs for [today]. */
    fun windowStart(today: LocalDate): LocalDate = mondayOf(today).minusWeeks(LOOK_BACK_WEEKS)

    /**
     * [completedDates] holds one entry per completed workout (its start date; duplicates count).
     * Returns at most [LOOK_BACK_WEEKS] + 1.
     */
    fun compute(completedDates: List<LocalDate>, daysPerWeek: Int, today: LocalDate): Int {
        val needed = daysPerWeek.coerceAtLeast(1)
        val perWeek = completedDates.groupingBy { mondayOf(it) }.eachCount()
        fun complete(monday: LocalDate) = (perWeek[monday] ?: 0) >= needed

        val thisWeek = mondayOf(today)
        var streak = if (complete(thisWeek)) 1 else 0
        var week = thisWeek.minusWeeks(1)
        var looked = 0L
        while (looked < LOOK_BACK_WEEKS && complete(week)) {
            streak++
            looked++
            week = week.minusWeeks(1)
        }
        return streak
    }
}
