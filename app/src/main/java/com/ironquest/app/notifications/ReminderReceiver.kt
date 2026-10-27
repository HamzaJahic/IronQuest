package com.ironquest.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironquest.app.di.ApplicationScope
import com.ironquest.app.domain.nutrition.ReminderSchedule
import com.ironquest.app.domain.repository.NutritionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Alarm fired: re-check, maybe notify, then always arm the next occurrence. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: NutritionRepository

    @Inject lateinit var coordinator: ReminderCoordinator

    @Inject lateinit var notifier: ReminderNotifier

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_FIRE) return
        val target = ReminderIntents.target(intent) ?: return
        goAsyncIn(scope) {
            // Delivered: forget the stored trigger BEFORE re-arming so the next occurrence is scheduled.
            coordinator.markFired(ReminderSchedule.keyOf(target.kind, target.targetId))
            try {
                val day = LocalDate.ofEpochDay(target.targetEpochDay)
                // The slot may have been logged, removed, or its plan switched off since arming.
                val snapshot = repository.reminderSnapshot(day, day)
                if (ReminderSchedule.isStillDue(target, snapshot)) notifier.show(target, snapshot, LocalDate.now(clock))
            } finally {
                coordinator.rearmNow()
            }
        }
    }
}
