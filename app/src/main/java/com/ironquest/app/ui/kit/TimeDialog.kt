package com.ironquest.app.ui.kit

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ironquest.app.R
import com.ironquest.app.ui.theme.IronTheme

private const val DEFAULT_TIME_MIN = 12 * 60

/** Time picker (24-hour or 12-hour, following the device setting) on a `raised` dialog. [initialMin] / the result are minutes after midnight; null starts at 12:00. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initialMin: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = IronTheme.colors
    val start = initialMin ?: DEFAULT_TIME_MIN
    val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.raised,
        text = {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                TimePicker(
                    state = state,
                    layoutType = TimePickerLayoutType.Vertical,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = colors.paper,
                        selectorColor = colors.accent,
                        timeSelectorSelectedContainerColor = colors.accent,
                        timeSelectorSelectedContentColor = colors.onAccent,
                        timeSelectorUnselectedContainerColor = colors.paper,
                        timeSelectorUnselectedContentColor = colors.ink,
                    ),
                )
            }
        },
        confirmButton = { DialogButton(stringResource(R.string.kit_time_dialog_set), { onConfirm(state.hour * 60 + state.minute) }) },
        dismissButton = { DialogButton(stringResource(R.string.common_cancel), onDismiss, muted = true) },
    )
}
