package com.ironquest.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ironquest.app.di.ApplicationScope
import com.ironquest.app.domain.nutrition.ReminderKind
import com.ironquest.app.domain.nutrition.ReminderSchedule
import com.ironquest.app.domain.nutrition.ReminderTarget
import com.ironquest.app.domain.repository.NutritionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * MARK EATEN / TAKEN from the shade. Logs at 1× for the payload's target day. The repository
 * is idempotent (`alreadyDone`), so a tap after checking it in the app is a harmless no-op.
 */
@AndroidEntryPoint
class MarkEatenReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: NutritionRepository

    @Inject lateinit var coordinator: ReminderCoordinator

    @Inject lateinit var notifier: ReminderNotifier

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderIntents.ACTION_MARK_DONE) return
        val target = ReminderIntents.target(intent) ?: return
        goAsyncIn(scope) {
            try {
                markIfDue(target)
            } finally {
                coordinator.rearmNow()
            }
        }
    }

    private suspend fun markIfDue(target: ReminderTarget) {
        // The action is never shown for a future target; this guards stale intents anyway.
        if (!ReminderSchedule.canMarkFromShade(target.targetEpochDay, LocalDate.now(clock))) return
        val date = LocalDate.ofEpochDay(target.targetEpochDay)
        try {
            when (target.kind) {
                ReminderKind.SLOT -> repository.logSlot(date, target.targetId, 1.0)
                ReminderKind.SUPPLEMENT -> repository.setSupplementTaken(date, target.targetId, taken = true)
            }
        } finally {
            // Also on failure (e.g. slot deleted meanwhile): the notification is stale either way.
            notifier.cancel(target)
        }
    }
}
