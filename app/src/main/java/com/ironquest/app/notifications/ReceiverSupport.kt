package com.ironquest.app.notifications

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the receiver alive via goAsync(), then finishes it. */
internal fun BroadcastReceiver.goAsyncIn(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w(ReminderCoordinator.TAG, "Receiver work failed", error)
        } finally {
            pending.finish()
        }
    }
}
