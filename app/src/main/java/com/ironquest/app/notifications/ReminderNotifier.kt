package com.ironquest.app.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.ReminderKind
import com.ironquest.app.domain.nutrition.ReminderSchedule
import com.ironquest.app.domain.nutrition.ReminderSnapshot
import com.ironquest.app.domain.nutrition.ReminderTarget
import com.ironquest.app.ui.text.resolve
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.notification_channel_meals_name))
                .setDescription(context.getString(R.string.notification_channel_meals_description))
                .build(),
        )
    }

    /** Posts the reminder for [target]; does nothing if it's no longer in [snapshot]. */
    fun show(target: ReminderTarget, snapshot: ReminderSnapshot, today: LocalDate) {
        val canMark = ReminderSchedule.canMarkFromShade(target.targetEpochDay, today)
        val builder = when (target.kind) {
            ReminderKind.SLOT -> {
                val slot = snapshot.slots.firstOrNull { it.id == target.targetId } ?: return
                base(target)
                    .setContentTitle(ReminderCopy.slotTitle(slot.label, slot.reminderOffsetMin ?: 0).resolve(context.resources))
                    .setContentText(ReminderCopy.slotBody(slot.mealName, slot.mealMacros).resolve(context.resources))
                    .also { if (canMark) it.addAction(0, ReminderCopy.markEaten.resolve(context.resources), markDone(target)) }
            }
            ReminderKind.SUPPLEMENT -> {
                val supplement = snapshot.supplements.firstOrNull { it.id == target.targetId } ?: return
                base(target)
                    .setContentTitle(ReminderCopy.supplementTitle(supplement.name, supplement.dose).resolve(context.resources))
                    .setContentText(ReminderCopy.supplementBody.resolve(context.resources))
                    .also { if (canMark) it.addAction(0, ReminderCopy.taken.resolve(context.resources), markDone(target)) }
            }
        }
        try {
            manager.notify(target.notificationId, builder.build())
        } catch (denied: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): nothing to show.
        }
    }

    fun cancel(target: ReminderTarget) = manager.cancel(target.notificationId)

    /** Removes posted reminders that are no longer due (logged in the app, plan switched, reminder cleared). */
    fun cancelStale(snapshot: ReminderSnapshot) {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.activeNotifications.forEach { posted ->
            val target = ReminderIntents.target(posted.notification.extras) ?: return@forEach
            if (!ReminderSchedule.isStillDue(target, snapshot)) manager.cancel(posted.id)
        }
    }

    private fun base(target: ReminderTarget): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_food)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(context, target.notificationId, ReminderIntents.openFood(context), FLAGS),
        )
        .addExtras(ReminderIntents.extras(target))

    private fun markDone(target: ReminderTarget): PendingIntent = PendingIntent.getBroadcast(context, target.notificationId, ReminderIntents.markDone(context, target), FLAGS)

    companion object {
        const val CHANNEL_ID = "meal_reminders"
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
