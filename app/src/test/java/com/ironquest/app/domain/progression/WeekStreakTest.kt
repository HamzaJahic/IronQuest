package com.ironquest.app.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekStreakTest {
    // Friday 9 Oct 2026; this week starts Monday 5 Oct.
    private val today = LocalDate.of(2026, 10, 9)
    private val thisMonday = LocalDate.of(2026, 10, 5)

    private fun week(monday: LocalDate, workouts: Int) = (0 until workouts).map { monday.plusDays(it.toLong()) }

    @Test
    fun `consecutive full weeks count, a short week breaks the streak`() {
        val dates = week(thisMonday.minusWeeks(1), 4) + week(thisMonday.minusWeeks(2), 4) +
            week(thisMonday.minusWeeks(3), 3) + week(thisMonday.minusWeeks(4), 4)
        assertEquals(2, WeekStreak.compute(dates, daysPerWeek = 4, today = today))
    }

    @Test
    fun `the running week adds one once complete and never breaks the streak before`() {
        val past = week(thisMonday.minusWeeks(1), 4)
        assertEquals(1, WeekStreak.compute(past + week(thisMonday, 2), 4, today))
        assertEquals(2, WeekStreak.compute(past + week(thisMonday, 4), 4, today))
        assertEquals(0, WeekStreak.compute(week(thisMonday, 3), 4, today))
        assertEquals(1, WeekStreak.compute(week(thisMonday, 4), 4, today))
    }

    @Test
    fun `two workouts on one day both count and weeks are Monday to Sunday`() {
        val sunday = thisMonday.minusDays(1)
        val lastWeek = listOf(sunday, sunday, sunday.minusDays(6)) // Mon 28 Sep + Sun 4 Oct twice
        assertEquals(1, WeekStreak.compute(lastWeek, 3, today))
        assertEquals(0, WeekStreak.compute(lastWeek, 4, today))
    }

    @Test
    fun `look-back is bounded`() {
        val dates = (1..200L).flatMap { week(thisMonday.minusWeeks(it), 1) }
        assertEquals(WeekStreak.LOOK_BACK_WEEKS.toInt(), WeekStreak.compute(dates, 1, today))
        assertEquals(thisMonday.minusWeeks(104), WeekStreak.windowStart(today))
    }
}
