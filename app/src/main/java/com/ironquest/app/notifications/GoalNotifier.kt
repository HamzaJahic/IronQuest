package com.ironquest.app.notifications

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalReminder
import com.ironquest.app.domain.goals.GoalReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the daily goal notifications on the "Goals" channel. Tap: Today (workout) or Food. */
@Singleton
class GoalNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_goals_name))
                .setDescription(context.getString(R.string.notification_channel_goals_description))
                .build(),
        )
    }

    fun show(reminder: GoalReminder, title: String, body: String, openRoute: String) {
        val id = GoalReminderSchedule.requestCode(reminder)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_food)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    id,
                    ReminderIntents.openRoute(context, openRoute),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        try {
            manager.notify(id, notification)
        } catch (denied: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): nothing to show.
        }
    }

    companion object {
        const val CHANNEL_ID = "goals"
    }
}
