package com.ironquest.app.domain.nutrition

data class ScheduledReminder(
    val key: String,
    val triggerAtMillis: Long,
    val kind: ReminderKind,
    val targetId: Long,
    /**
     * Date of the meal/supplement time, NOT of the trigger: a 00:10 slot with a 15-min
     * offset triggers at 23:55 the previous day but targets the next day.
     */
    val targetEpochDay: Long,
) {
    val requestCode: Int get() = ReminderSchedule.requestCode(kind, targetId)
    val target: ReminderTarget get() = ReminderTarget(kind, targetId, targetEpochDay)
}
