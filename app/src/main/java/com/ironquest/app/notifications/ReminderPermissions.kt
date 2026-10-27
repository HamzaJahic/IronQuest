package com.ironquest.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** API-level-aware permission checks and settings intents for reminders. */
object ReminderPermissions {

    /** True on API 33+ while POST_NOTIFICATIONS hasn't been granted (a runtime prompt is possible). */
    fun needsNotificationPermission(context: Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private const val PREFS = "reminder_permissions"
    private const val KEY_ASKED = "asked_notification_permission"

    /** Remembers that the runtime prompt was shown, so a permanent denial can later surface the "Notifications are off" hint. */
    fun markAskedNotificationPermission(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ASKED, true).apply()
    }

    fun askedNotificationPermission(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    fun notificationsEnabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** True when the user muted the reminder channel itself (importance NONE, API 26+). */
    fun reminderChannelMuted(context: Context): Boolean = NotificationManagerCompat.from(context).getNotificationChannelCompat(ReminderNotifier.CHANNEL_ID)
        ?.importance == NotificationManagerCompat.IMPORTANCE_NONE

    /** Below API 31 exact alarms need no permission. */
    fun canScheduleExact(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun exactAlarmSettings(context: Context): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")) // not UI text: package URI
    } else {
        appDetails(context)
    }

    fun notificationSettings(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Opens [intent], falling back to the app-details page on devices without that screen. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (notFound: ActivityNotFoundException) {
            context.startActivity(appDetails(context))
        }
    }

    private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")) // not UI text: package URI
}
