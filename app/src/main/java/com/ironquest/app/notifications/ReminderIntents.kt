package com.ironquest.app.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.ironquest.app.MainActivity
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.nutrition.ReminderKind
import com.ironquest.app.domain.nutrition.ReminderTarget
import com.ironquest.app.ui.navigation.Routes

/** Actions, extras and intents shared by the scheduler, the notifier and the receivers. */
object ReminderIntents {
    const val ACTION_FIRE = "com.ironquest.app.action.REMINDER_FIRE" // not UI text: intent action or extra key
    const val ACTION_MARK_DONE = "com.ironquest.app.action.REMINDER_MARK_DONE" // not UI text: intent action or extra key
    const val ACTION_GOAL_FIRE = "com.ironquest.app.action.GOAL_REMINDER_FIRE" // not UI text: intent action or extra key

    /** MainActivity extra: top-level route to open (notification tap → Food). */
    const val EXTRA_OPEN_ROUTE = "com.ironquest.app.extra.OPEN_ROUTE" // not UI text: intent action or extra key

    private const val EXTRA_KIND = "com.ironquest.app.extra.REMINDER_KIND" // not UI text: intent action or extra key
    private const val EXTRA_TARGET_ID = "com.ironquest.app.extra.REMINDER_TARGET_ID" // not UI text: intent action or extra key
    private const val EXTRA_TARGET_DAY = "com.ironquest.app.extra.REMINDER_TARGET_DAY" // not UI text: intent action or extra key
    private const val EXTRA_GOAL_REMINDER = "com.ironquest.app.extra.GOAL_REMINDER" // not UI text: intent action or extra key

    fun extras(target: ReminderTarget): Bundle = Bundle().apply {
        putString(EXTRA_KIND, target.kind.name)
        putLong(EXTRA_TARGET_ID, target.targetId)
        putLong(EXTRA_TARGET_DAY, target.targetEpochDay)
    }

    fun target(extras: Bundle?): ReminderTarget? {
        if (extras == null || !extras.containsKey(EXTRA_TARGET_ID) || !extras.containsKey(EXTRA_TARGET_DAY)) return null
        val kind = ReminderKind.entries.firstOrNull { it.name == extras.getString(EXTRA_KIND) } ?: return null
        return ReminderTarget(kind, extras.getLong(EXTRA_TARGET_ID), extras.getLong(EXTRA_TARGET_DAY))
    }

    fun target(intent: Intent): ReminderTarget? = target(intent.extras)

    /** Extras don't take part in PendingIntent matching, so [fireTemplate] finds the alarm again for cancel. */
    fun fireTemplate(context: Context): Intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)

    fun fire(context: Context, target: ReminderTarget): Intent = fireTemplate(context).putExtras(extras(target))

    fun markDone(context: Context, target: ReminderTarget): Intent = Intent(context, MarkEatenReceiver::class.java).setAction(ACTION_MARK_DONE).putExtras(extras(target))

    fun openFood(context: Context): Intent = openRoute(context, Routes.FOOD)

    /** Opens MainActivity on a top-level tab ([Routes.TODAY] for workout goals, [Routes.FOOD] for food). */
    fun openRoute(context: Context, route: String): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN_ROUTE, route)

    /** Goal alarms: extras don't take part in matching, so this also finds the alarm again for cancel. */
    fun goalFireTemplate(context: Context): Intent = Intent(context, GoalReminderReceiver::class.java).setAction(ACTION_GOAL_FIRE)

    fun goalFire(context: Context, reminder: GoalReminder): Intent = goalFireTemplate(context).putExtra(EXTRA_GOAL_REMINDER, reminder.name)

    fun goalReminder(intent: Intent): GoalReminder? = GoalReminder.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_GOAL_REMINDER) }
}
