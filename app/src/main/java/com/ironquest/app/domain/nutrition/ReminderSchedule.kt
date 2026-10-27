package com.ironquest.app.domain.nutrition

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure reminder rules. Only the next occurrence of each reminder is scheduled;
 * the alarm re-arms when it fires. Already-past triggers roll to the next day, and so do
 * occurrences already logged/taken for their target date.
 */
object ReminderSchedule {
    /** Target days searched ahead of today. Two always suffice (max offset 60 min); one spare. */
    const val SEARCH_DAYS = 3L

    fun compute(snapshot: ReminderSnapshot, clock: Clock): List<ScheduledReminder> {
        val now = clock.instant()
        val zone = clock.zone
        // Not LocalDate.ofInstant: that's Java 9+, unavailable on API 26–33 without desugaring.
        val today = now.atZone(zone).toLocalDate()
        val slotReminders = snapshot.slots.mapNotNull { slot ->
            val time = slot.timeOfDayMin ?: return@mapNotNull null
            val offset = slot.reminderOffsetMin ?: return@mapNotNull null
            next(ReminderKind.SLOT, slot.id, time, offset, snapshot.loggedSlots, now, zone, today)
        }
        val supplementReminders = snapshot.supplements.mapNotNull { supplement ->
            val time = supplement.reminderTimeMin ?: return@mapNotNull null
            next(ReminderKind.SUPPLEMENT, supplement.id, time, 0, snapshot.takenSupplements, now, zone, today)
        }
        return (slotReminders + supplementReminders).sortedBy { it.triggerAtMillis }
    }

    /** How long an alarm may be overdue-but-undelivered (inexact alarms drift) before it counts as missed. */
    const val PENDING_GRACE_MILLIS = 2 * 60 * 60 * 1000L

    /**
     * True when a previously armed alarm is due but not yet delivered, so a re-arm must leave it
     * alone (replacing it with the next occurrence would lose today's notification).
     */
    fun keepPending(storedTriggerMillis: Long?, nowMillis: Long, graceMillis: Long): Boolean = storedTriggerMillis != null && storedTriggerMillis <= nowMillis && nowMillis - storedTriggerMillis <= graceMillis

    /** Re-check at fire time: still configured, still in the active plan, not yet done for its target day. */
    fun isStillDue(target: ReminderTarget, snapshot: ReminderSnapshot): Boolean {
        val done = DayKey(target.targetEpochDay, target.targetId)
        return when (target.kind) {
            ReminderKind.SLOT ->
                snapshot.slots.any { it.id == target.targetId && it.timeOfDayMin != null && it.reminderOffsetMin != null } &&
                    done !in snapshot.loggedSlots
            ReminderKind.SUPPLEMENT ->
                snapshot.supplements.any { it.id == target.targetId && it.reminderTimeMin != null } &&
                    done !in snapshot.takenSupplements
        }
    }

    /** The repository rejects future dates, so a reminder for tomorrow's 00:10 meal can't be marked at 23:55. */
    fun canMarkFromShade(targetEpochDay: Long, today: LocalDate): Boolean = targetEpochDay <= today.toEpochDay()

    fun keyOf(kind: ReminderKind, id: Long): String = "${kind.prefix}:$id" // not UI text

    fun parseKey(key: String): Pair<ReminderKind, Long>? {
        val parts = key.split(':')
        if (parts.size != 2) return null
        val kind = ReminderKind.entries.firstOrNull { it.prefix == parts[0] } ?: return null
        val id = parts[1].toLongOrNull() ?: return null
        return kind to id
    }

    /** Stable per (kind, id); doubles as the notification id. Unique while ids stay below 2^30. */
    fun requestCode(kind: ReminderKind, id: Long): Int = ((id shl 1) or kind.ordinal.toLong()).toInt()

    private fun next(
        kind: ReminderKind,
        id: Long,
        timeOfDayMin: Int,
        offsetMin: Int,
        done: Set<DayKey>,
        now: Instant,
        zone: ZoneId,
        today: LocalDate,
    ): ScheduledReminder? {
        // A corrupt row must not abort compute() for every other reminder.
        if (timeOfDayMin !in 0..1439) return null
        for (dayOffset in 0..SEARCH_DAYS) {
            val date = today.plusDays(dayOffset)
            if (DayKey(date.toEpochDay(), id) in done) continue
            // ZonedDateTime.of shifts a time inside a DST gap forward; minusMinutes works on the instant timeline.
            val trigger = ZonedDateTime.of(date, LocalTime.of(timeOfDayMin / 60, timeOfDayMin % 60), zone)
                .minusMinutes(offsetMin.toLong())
                .toInstant()
            if (trigger.isAfter(now)) {
                return ScheduledReminder(keyOf(kind, id), trigger.toEpochMilli(), kind, id, date.toEpochDay())
            }
        }
        return null
    }
}
