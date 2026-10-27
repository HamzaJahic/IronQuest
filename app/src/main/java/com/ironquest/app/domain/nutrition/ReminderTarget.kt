package com.ironquest.app.domain.nutrition

/** What a reminder is about. [targetEpochDay] is the date of the meal/dose time. */
data class ReminderTarget(val kind: ReminderKind, val targetId: Long, val targetEpochDay: Long) {
    val notificationId: Int get() = ReminderSchedule.requestCode(kind, targetId)
}
