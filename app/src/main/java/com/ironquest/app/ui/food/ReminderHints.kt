package com.ironquest.app.ui.food

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ironquest.app.R
import com.ironquest.app.notifications.ReminderPermissions
import com.ironquest.app.ui.kit.TextAction
import com.ironquest.app.ui.theme.IronTheme

/**
 * Returns `(hasReminder, save) -> Unit`. It always runs `save` first, then asks for
 * POST_NOTIFICATIONS (API 33+) if a reminder is set and it isn't granted. Saving first means
 * a rotation or a dismissed dialog can never lose the edit; the system keeps the answer even
 * if the editor has already closed. Android stops showing the prompt after two denials.
 */
@Composable
fun rememberReminderPermissionGate(): (hasReminder: Boolean, save: () -> Unit) -> Unit {
    val context = LocalContext.current
    // The result callback is empty on purpose: the plan editor pops on Saved while the system
    // dialog is still up, which unregisters this launcher and drops the result. The "asked" flag
    // is therefore set at launch time; hints re-check permission on resume anyway.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(context, launcher) {
        { hasReminder, save ->
            save()
            if (hasReminder && ReminderPermissions.needsNotificationPermission(context)) {
                ReminderPermissions.markAskedNotificationPermission(context)
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Shown while a reminder is configured. Re-checked on every resume, so coming back from
 * Settings updates it.
 */
@Composable
fun ReminderPermissionHints(active: Boolean) {
    if (!active) return
    val context = LocalContext.current
    var exactAllowed by remember { mutableStateOf(true) }
    var notificationsBlocked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        exactAllowed = ReminderPermissions.canScheduleExact(context)
        // Below API 33 there is no prompt, so disabled means blocked. On 33+ only once we've asked
        // (a permanent denial leaves needsNotificationPermission true forever) - never warn before asking.
        notificationsBlocked = ReminderPermissions.reminderChannelMuted(context) ||
            (
                !ReminderPermissions.notificationsEnabled(context) &&
                    (
                        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                            ReminderPermissions.askedNotificationPermission(context)
                        )
                )
        onPauseOrDispose { }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (notificationsBlocked) {
            HintRow(stringResource(R.string.food_hint_notifications_off), stringResource(R.string.food_hint_notifications_action)) {
                ReminderPermissions.open(context, ReminderPermissions.notificationSettings(context))
            }
        }
        if (!exactAllowed) {
            HintRow(stringResource(R.string.food_hint_exact_alarms), stringResource(R.string.food_hint_exact_alarms_action)) {
                ReminderPermissions.open(context, ReminderPermissions.exactAlarmSettings(context))
            }
        }
    }
}

@Composable
private fun HintRow(text: String, action: String, onClick: () -> Unit) {
    val colors = IronTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(IronTheme.shapes.tile).background(colors.holdBg).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = IronTheme.type.secondary, color = colors.hold, modifier = Modifier.weight(1f))
        TextAction(action, onClick, strong = true)
    }
}
