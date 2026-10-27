package com.ironquest.app.domain.goals

import com.ironquest.app.domain.nutrition.ReminderKind
import com.ironquest.app.domain.nutrition.ReminderSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

class GoalReminderScheduleTest {
    private fun at(hour: Int, minute: Int = 0) = Clock.fixed(LocalDateTime.of(2026, 10, 9, hour, minute).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    @Test
    fun `next occurrence is today while ahead, otherwise tomorrow`() {
        val morning = GoalReminderSchedule.next(GoalReminder.WORKOUT, 8 * 60, at(7, 59))
        assertEquals(LocalDateTime.of(2026, 10, 9, 8, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), morning.triggerAtMillis)
        val late = GoalReminderSchedule.next(GoalReminder.WORKOUT, 8 * 60, at(8, 0))
        assertEquals(LocalDateTime.of(2026, 10, 10, 8, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), late.triggerAtMillis)
        assertEquals(late.targetEpochDay, morning.targetEpochDay + 1)
    }

    @Test
    fun `alarms follow show and notify switches`() {
        assertEquals(2, GoalReminderSchedule.compute(GoalSettings.Default, at(12)).size)
        val off = GoalSettings.Default.let { settings ->
            settings.copy(
                byType = settings.byType +
                    (GoalType.LIMIT_BREAK to GoalTypeSettings(notify = false)) +
                    (GoalType.BEAT_LAST_TIME to GoalTypeSettings(show = false)) +
                    (GoalType.FOOD to GoalTypeSettings(notify = false)),
            )
        }
        assertTrue(GoalReminderSchedule.compute(off, at(12)).isEmpty())
    }

    @Test
    fun `request codes never collide with meal reminder codes`() {
        val codes = GoalReminder.entries.map(GoalReminderSchedule::requestCode)
        assertEquals(codes.size, codes.toSet().size)
        for (id in listOf(0L, 1L, (1L shl 30) - 1)) {
            ReminderKind.entries.forEach { kind -> assertTrue(ReminderSchedule.requestCode(kind, id) !in codes) }
        }
    }
}
