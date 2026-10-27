package com.ironquest.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironquest.app.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/** Alarms don't survive reboots, and wall-clock changes move trigger instants: re-arm. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var coordinator: ReminderCoordinator

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        // The cached default zone is stale after a zone change; LiveZoneClock reads it.
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) java.util.TimeZone.setDefault(null)
        SafetyRearmReceiver.schedule(context)
        goAsyncIn(scope) { coordinator.rearmNow() }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED, // not UI text: "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+), as a literal for minSdk 26.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED", // not UI text: system broadcast action
        )
    }
}
