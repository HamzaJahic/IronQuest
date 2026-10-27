package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ReminderScheduleTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 5)
    private val macros = Macros(35.0, 75.0, 9.0, 9.0)

    private fun clockAt(hour: Int, minute: Int, zone: ZoneId = utc, date: LocalDate = today): Clock = Clock.fixed(date.atTime(hour, minute).atZone(zone).toInstant(), zone)

    private fun millis(date: LocalDate, hour: Int, minute: Int, zone: ZoneId = utc): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun slot(id: Long, time: Int?, offset: Int?, label: String = "Breakfast") = PlanSlot(id, label, mealId = 1, mealName = "Oats", mealMacros = macros, timeOfDayMin = time, reminderOffsetMin = offset)

    private fun supplement(id: Long, time: Int?) = ReminderSupplement(id, "Creatine", "5 g", time)

    private fun slotReminder(id: Long, triggerAt: Long, target: LocalDate) = ScheduledReminder("slot:$id", triggerAt, ReminderKind.SLOT, id, target.toEpochDay())

    @Test
    fun `a slot later today triggers offset minutes before its time`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 8 * 60, 15))), clockAt(6, 0))
        assertEquals(listOf(slotReminder(21, millis(today, 7, 45), today)), result)
    }

    @Test
    fun `a trigger already in the past rolls to tomorrow`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 8 * 60, 15))), clockAt(7, 50))
        assertEquals(listOf(slotReminder(21, millis(today.plusDays(1), 7, 45), today.plusDays(1))), result)
    }

    @Test
    fun `a trigger exactly now counts as past`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 8 * 60, 15))), clockAt(7, 45))
        assertEquals(today.plusDays(1).toEpochDay(), result.single().targetEpochDay)
    }

    @Test
    fun `a slot already logged today rolls to tomorrow`() {
        val snapshot = ReminderSnapshot(
            slots = listOf(slot(21, 8 * 60, 15)),
            loggedSlots = setOf(DayKey(today.toEpochDay(), 21)),
        )
        val result = ReminderSchedule.compute(snapshot, clockAt(6, 0))
        assertEquals(listOf(slotReminder(21, millis(today.plusDays(1), 7, 45), today.plusDays(1))), result)
    }

    @Test
    fun `logs of other slots or other days do not matter`() {
        val snapshot = ReminderSnapshot(
            slots = listOf(slot(21, 8 * 60, 15)),
            loggedSlots = setOf(DayKey(today.toEpochDay(), 22), DayKey(today.minusDays(1).toEpochDay(), 21)),
        )
        assertEquals(today.toEpochDay(), ReminderSchedule.compute(snapshot, clockAt(6, 0)).single().targetEpochDay)
    }

    @Test
    fun `a midnight-crossing offset triggers the evening before and targets the next day`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 10, 15))), clockAt(22, 0))
        assertEquals(listOf(slotReminder(21, millis(today, 23, 55), today.plusDays(1))), result)
    }

    @Test
    fun `a midnight-crossing trigger already passed rolls a full day`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 10, 15))), clockAt(23, 58))
        assertEquals(listOf(slotReminder(21, millis(today.plusDays(1), 23, 55), today.plusDays(2))), result)
    }

    @Test
    fun `just after midnight the next reminder is tonight for tomorrow`() {
        val result = ReminderSchedule.compute(ReminderSnapshot(slots = listOf(slot(21, 10, 15))), clockAt(0, 5))
        assertEquals(listOf(slotReminder(21, millis(today, 23, 55), today.plusDays(1))), result)
    }

    @Test
    fun `slots without a time or without an offset get no reminder`() {
        val snapshot = ReminderSnapshot(slots = listOf(slot(1, null, null), slot(2, 8 * 60, null)))
        assertTrue(ReminderSchedule.compute(snapshot, clockAt(6, 0)).isEmpty())
    }

    @Test
    fun `no active plan and no supplement reminders gives nothing`() {
        assertTrue(ReminderSchedule.compute(ReminderSnapshot(), clockAt(6, 0)).isEmpty())
        val noTime = ReminderSnapshot(supplements = listOf(supplement(5, null)))
        assertTrue(ReminderSchedule.compute(noTime, clockAt(6, 0)).isEmpty())
    }

    @Test
    fun `a supplement fires at its time and rolls once taken`() {
        val pending = ReminderSnapshot(supplements = listOf(supplement(5, 8 * 60)))
        assertEquals(
            listOf(ScheduledReminder("supplement:5", millis(today, 8, 0), ReminderKind.SUPPLEMENT, 5, today.toEpochDay())),
            ReminderSchedule.compute(pending, clockAt(6, 0)),
        )
        val taken = pending.copy(takenSupplements = setOf(DayKey(today.toEpochDay(), 5)))
        assertEquals(millis(today.plusDays(1), 8, 0), ReminderSchedule.compute(taken, clockAt(6, 0)).single().triggerAtMillis)
    }

    @Test
    fun `the clock zone decides which day is today and the wall-clock trigger`() {
        val snapshot = ReminderSnapshot(slots = listOf(slot(21, 8 * 60, 15)))
        val instant = Instant.parse("2026-10-05T03:00:00Z")

        // New York (EDT, UTC-4): still Oct 4, 23:00 → next 07:45 is Oct 5 local = 11:45Z.
        val ny = ReminderSchedule.compute(snapshot, Clock.fixed(instant, ZoneId.of("America/New_York"))).single()
        assertEquals(Instant.parse("2026-10-05T11:45:00Z").toEpochMilli(), ny.triggerAtMillis)
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), ny.targetEpochDay)

        // Tokyo (UTC+9): Oct 5, 12:00 → 07:45 passed → Oct 6 07:45 JST = Oct 5 22:45Z.
        val tokyo = ReminderSchedule.compute(snapshot, Clock.fixed(instant, ZoneId.of("Asia/Tokyo"))).single()
        assertEquals(Instant.parse("2026-10-05T22:45:00Z").toEpochMilli(), tokyo.triggerAtMillis)
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(), tokyo.targetEpochDay)
    }

    @Test
    fun `a time inside a DST gap is shifted forward, not dropped`() {
        // Europe/Berlin jumps 02:00 → 03:00 on 2026-03-29; 02:30 doesn't exist and becomes 03:30 CEST (01:30Z).
        val berlin = ZoneId.of("Europe/Berlin")
        val clock = Clock.fixed(Instant.parse("2026-03-28T23:30:00Z"), berlin) // 00:30 CET on the 29th
        val result = ReminderSchedule.compute(ReminderSnapshot(supplements = listOf(supplement(5, 2 * 60 + 30))), clock).single()
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), result.triggerAtMillis)
        assertEquals(LocalDate.of(2026, 3, 29).toEpochDay(), result.targetEpochDay)
    }

    @Test
    fun `results are sorted by trigger time`() {
        val snapshot = ReminderSnapshot(slots = listOf(slot(21, 12 * 60, 30)), supplements = listOf(supplement(5, 8 * 60)))
        assertEquals(listOf("supplement:5", "slot:21"), ReminderSchedule.compute(snapshot, clockAt(6, 0)).map { it.key })
    }

    @Test
    fun `keys and request codes are stable and distinct per kind`() {
        assertEquals("slot:21", ReminderSchedule.keyOf(ReminderKind.SLOT, 21))
        assertEquals(ReminderKind.SLOT to 21L, ReminderSchedule.parseKey("slot:21"))
        assertEquals(ReminderKind.SUPPLEMENT to 5L, ReminderSchedule.parseKey("supplement:5"))
        assertNull(ReminderSchedule.parseKey("meal:1"))
        assertNull(ReminderSchedule.parseKey("slot:x"))
        assertNull(ReminderSchedule.parseKey("garbage"))
        assertEquals(42, ReminderSchedule.requestCode(ReminderKind.SLOT, 21))
        assertEquals(43, ReminderSchedule.requestCode(ReminderKind.SUPPLEMENT, 21))
        assertEquals(42, slotReminder(21, 0, today).requestCode)
        assertEquals(42, ReminderTarget(ReminderKind.SLOT, 21, 0).notificationId)
    }

    @Test
    fun `a slot is still due only while active, with a reminder, and not logged for its target day`() {
        val target = ReminderTarget(ReminderKind.SLOT, 21, today.toEpochDay())
        val withSlot = ReminderSnapshot(slots = listOf(slot(21, 8 * 60, 15)))
        assertTrue(ReminderSchedule.isStillDue(target, withSlot))
        assertFalse("plan deactivated or slot removed", ReminderSchedule.isStillDue(target, ReminderSnapshot()))
        assertFalse("reminder switched off", ReminderSchedule.isStillDue(target, ReminderSnapshot(slots = listOf(slot(21, 8 * 60, null)))))
        assertFalse("already logged", ReminderSchedule.isStillDue(target, withSlot.copy(loggedSlots = setOf(DayKey(today.toEpochDay(), 21)))))
        assertTrue("logged another day", ReminderSchedule.isStillDue(target, withSlot.copy(loggedSlots = setOf(DayKey(today.minusDays(1).toEpochDay(), 21)))))
    }

    @Test
    fun `a supplement is still due only while it has a reminder and is not taken`() {
        val target = ReminderTarget(ReminderKind.SUPPLEMENT, 5, today.toEpochDay())
        val pending = ReminderSnapshot(supplements = listOf(supplement(5, 8 * 60)))
        assertTrue(ReminderSchedule.isStillDue(target, pending))
        assertFalse(ReminderSchedule.isStillDue(target, ReminderSnapshot(supplements = listOf(supplement(5, null)))))
        assertFalse(ReminderSchedule.isStillDue(target, ReminderSnapshot()))
        assertFalse(ReminderSchedule.isStillDue(target, pending.copy(takenSupplements = setOf(DayKey(today.toEpochDay(), 5)))))
    }

    @Test
    fun `marking from the notification is only offered for today or earlier`() {
        assertTrue(ReminderSchedule.canMarkFromShade(today.toEpochDay(), today))
        assertTrue(ReminderSchedule.canMarkFromShade(today.minusDays(1).toEpochDay(), today))
        assertFalse(ReminderSchedule.canMarkFromShade(today.plusDays(1).toEpochDay(), today))
    }

    @Test
    fun `an out-of-range time of day is skipped without affecting other reminders`() {
        val snapshot = ReminderSnapshot(
            slots = listOf(slot(1, 1440, 15), slot(2, -1, 15), slot(21, 8 * 60, 15)),
            supplements = listOf(supplement(5, 5000)),
        )
        val result = ReminderSchedule.compute(snapshot, clockAt(6, 0))
        assertEquals(listOf(slotReminder(21, millis(today, 7, 45), today)), result)
    }

    @Test
    fun `a time in a DST overlap uses the first occurrence`() {
        val berlin = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 10, 25)
        val result = ReminderSchedule.compute(
            ReminderSnapshot(supplements = listOf(supplement(5, 2 * 60 + 30))),
            clockAt(12, 0, berlin, day.minusDays(1)),
        )
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), result.single().triggerAtMillis)
        assertEquals(day.toEpochDay(), result.single().targetEpochDay)
    }

    @Test
    fun `several consecutive logged days roll to the first unlogged one`() {
        val snapshot = ReminderSnapshot(
            slots = listOf(slot(21, 8 * 60, 15)),
            loggedSlots = setOf(DayKey(today.toEpochDay(), 21), DayKey(today.plusDays(1).toEpochDay(), 21)),
        )
        val result = ReminderSchedule.compute(snapshot, clockAt(6, 0))
        assertEquals(listOf(slotReminder(21, millis(today.plusDays(2), 7, 45), today.plusDays(2))), result)
    }

    @Test
    fun `a midnight-crossing slot logged today targets tomorrow and triggers tonight`() {
        val snapshot = ReminderSnapshot(
            slots = listOf(slot(21, 10, 15)),
            loggedSlots = setOf(DayKey(today.toEpochDay(), 21)),
        )
        val result = ReminderSchedule.compute(snapshot, clockAt(12, 0))
        assertEquals(listOf(slotReminder(21, millis(today, 23, 55), today.plusDays(1))), result)
    }

    private val grace = ReminderSchedule.PENDING_GRACE_MILLIS

    @Test
    fun `keepPending is false without a stored trigger`() {
        assertFalse(ReminderSchedule.keepPending(null, 1_000, grace))
    }

    @Test
    fun `keepPending is false for a future stored trigger`() {
        assertFalse(ReminderSchedule.keepPending(1_001, 1_000, grace))
    }

    @Test
    fun `keepPending is true when the trigger is exactly now`() {
        assertTrue(ReminderSchedule.keepPending(1_000, 1_000, grace))
    }

    @Test
    fun `keepPending is true when overdue by exactly the grace`() {
        assertTrue(ReminderSchedule.keepPending(1_000, 1_000 + grace, grace))
    }

    @Test
    fun `keepPending is false beyond the grace`() {
        assertFalse(ReminderSchedule.keepPending(1_000, 1_000 + grace + 1, grace))
    }

    @Test
    fun `grace is two hours`() {
        assertEquals(2 * 60 * 60 * 1000L, grace)
    }
}
