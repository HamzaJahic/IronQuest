package com.ironquest.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import com.ironquest.app.domain.goals.GoalAlarm
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalReminderSchedule
import com.ironquest.app.domain.nutrition.ReminderSchedule
import com.ironquest.app.domain.nutrition.ScheduledReminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms [ScheduledReminder]s with AlarmManager: exact (`setExactAndAllowWhileIdle`) when
 * allowed, otherwise `setAndAllowWhileIdle` (may be late). AlarmManager can't list alarms,
 * so the armed keys are kept in SharedPreferences to cancel ones that are no longer wanted.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun apply(reminders: List<ScheduledReminder>) {
        val wanted = reminders.map { it.key }.toSet()
        val previous = prefs.getStringSet(KEY_ARMED, emptySet()).orEmpty()
        (previous - wanted).forEach(::cancel)
        val exact = ReminderPermissions.canScheduleExact(context)
        val nowMillis = clock.millis()
        val edit = prefs.edit()
        (previous - wanted).forEach { edit.remove(triggerPref(it)) }
        for (reminder in reminders) {
            val stored = prefs.getString(triggerPref(reminder.key), null)?.substringBefore('|')?.toLongOrNull()
            if (ReminderSchedule.keepPending(stored, nowMillis, ReminderSchedule.PENDING_GRACE_MILLIS)) {
                // Due but not delivered yet (inexact alarm): leave it; the next re-arm after it fires moves on.
                continue
            }
            arm(reminder, exact)
            edit.putString(triggerPref(reminder.key), "${reminder.triggerAtMillis}|${reminder.targetEpochDay}") // not UI text: stored trigger value
        }
        // Never hand SharedPreferences a set it might alias.
        edit.putStringSet(KEY_ARMED, HashSet(wanted)).apply()
    }

    /**
     * Goal alarms ([GoalReminderSchedule]): fixed request codes, so the ones not in [alarms] are
     * simply cancelled. Same past-due grace as [apply], keyed by [GoalAlarm.key].
     */
    fun applyGoals(alarms: List<GoalAlarm>) {
        val exact = ReminderPermissions.canScheduleExact(context)
        val nowMillis = clock.millis()
        val edit = prefs.edit()
        for (reminder in GoalReminder.entries) {
            val key = GoalReminderSchedule.keyOf(reminder)
            val alarm = alarms.firstOrNull { it.reminder == reminder }
            if (alarm == null) {
                cancelGoal(reminder)
                edit.remove(triggerPref(key))
                continue
            }
            val stored = prefs.getString(triggerPref(key), null)?.substringBefore('|')?.toLongOrNull()
            if (ReminderSchedule.keepPending(stored, nowMillis, ReminderSchedule.PENDING_GRACE_MILLIS)) continue
            val pending = PendingIntent.getBroadcast(
                context,
                alarm.requestCode,
                ReminderIntents.goalFire(context, reminder),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setAlarm(alarm.triggerAtMillis, pending, exact)
            edit.putString(triggerPref(key), "${alarm.triggerAtMillis}|${alarm.targetEpochDay}") // not UI text: stored trigger value
        }
        edit.apply()
    }

    private fun cancelGoal(reminder: GoalReminder) {
        val pending = PendingIntent.getBroadcast(
            context,
            GoalReminderSchedule.requestCode(reminder),
            ReminderIntents.goalFireTemplate(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /** The alarm for [key] was delivered: forget its trigger so the next re-arm schedules the following occurrence. */
    fun markFired(key: String) {
        prefs.edit().remove(triggerPref(key)).apply()
    }

    private fun arm(reminder: ScheduledReminder, exact: Boolean) {
        // Same request code + FLAG_UPDATE_CURRENT replaces the previous alarm and its extras.
        val pending = PendingIntent.getBroadcast(
            context,
            reminder.requestCode,
            ReminderIntents.fire(context, reminder.target),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setAlarm(reminder.triggerAtMillis, pending, exact)
    }

    private fun setAlarm(triggerAtMillis: Long, pending: PendingIntent, exact: Boolean) {
        if (exact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                return
            } catch (denied: SecurityException) {
                // Exact-alarm access revoked between the check and the call: fall through.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    private fun cancel(key: String) {
        val (kind, id) = ReminderSchedule.parseKey(key) ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            ReminderSchedule.requestCode(kind, id),
            ReminderIntents.fireTemplate(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private companion object {
        const val PREFS = "reminders"
        const val KEY_ARMED = "armed_keys"
        fun triggerPref(key: String) = "trigger:$key" // not UI text: prefs key
    }
}
