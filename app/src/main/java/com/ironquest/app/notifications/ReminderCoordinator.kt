package com.ironquest.app.notifications

import android.util.Log
import com.ironquest.app.data.repository.OfflineNutritionRepository
import com.ironquest.app.di.ApplicationScope
import com.ironquest.app.domain.goals.GoalReminderSchedule
import com.ironquest.app.domain.nutrition.ReminderRearmer
import com.ironquest.app.domain.nutrition.ReminderSchedule
import com.ironquest.app.domain.repository.AppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot → [ReminderSchedule] → alarms; goal settings → [GoalReminderSchedule] → goal alarms. Injects the concrete [OfflineNutritionRepository]
 * (reads only) because the bound NutritionRepository is the re-arming decorator, which
 * depends on this class — injecting the interface would be a Dagger cycle.
 */
@Singleton
class ReminderCoordinator @Inject constructor(
    private val repository: OfflineNutritionRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val settings: AppSettingsRepository,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) : ReminderRearmer {

    /** Conflated: a burst of writes causes one re-arm. */
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()

    init {
        scope.launch {
            requests.receiveAsFlow().collect {
                try {
                    rearmNow()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    // An Error (not Exception) escapes and ends this loop until process restart; accepted.
                    Log.w(TAG, "Re-arm failed", error)
                }
            }
        }
        // Goal notification switches and times live in settings, not the database.
        scope.launch { settings.observeGoals().drop(1).collect { requestRearm() } }
        // The tracking mode decides whether plan slots get alarms (reminderSnapshot drops them without a plan).
        scope.launch { settings.observeFood().map { it.mode }.distinctUntilChanged().drop(1).collect { requestRearm() } }
    }

    /** Marks [key]'s alarm delivered (see [ReminderScheduler.markFired]). */
    fun markFired(key: String) = scheduler.markFired(key)

    /** Non-blocking; used after in-app writes and at app start. */
    override fun requestRearm() {
        requests.trySend(Unit)
    }

    /** Blocking re-arm for receivers, which must finish before `goAsync` ends. */
    suspend fun rearmNow(): Unit = mutex.withLock {
        val today = LocalDate.now(clock)
        // From yesterday so the stale-notification sweep sees late-night posts for today.
        val snapshot = repository.reminderSnapshot(today.minusDays(1), today.plusDays(ReminderSchedule.SEARCH_DAYS))
        val reminders = ReminderSchedule.compute(snapshot, clock)
        scheduler.apply(reminders)
        notifier.cancelStale(snapshot)
        val goalAlarms = GoalReminderSchedule.compute(settings.currentGoals(), clock)
        scheduler.applyGoals(goalAlarms)
        Log.i(TAG, "Armed ${reminders.size} reminder(s), ${goalAlarms.size} goal reminder(s)")
    }

    companion object {
        const val TAG = "IronQuestReminders"
    }
}
