package com.ironquest.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironquest.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Daily inexact alarm that re-arms reminders, in case a system event or an OEM kill dropped them. */
@AndroidEntryPoint
class SafetyRearmReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncIn(scope) { coordinator.rearmNow() }
    }

    companion object {
        /** Idempotent: the same PendingIntent replaces the previous repeating alarm. */
        fun schedule(context: Context) {
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SafetyRearmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService(AlarmManager::class.java).setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + AlarmManager.INTERVAL_DAY,
                AlarmManager.INTERVAL_DAY,
                pending,
            )
        }
    }
}
