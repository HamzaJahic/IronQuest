package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ironquest.app.ui.theme.IronTheme

/** A [RuledRow] with a title, optional secondary line and a switch; the whole row toggles. */
@Composable
fun IronSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
) {
    val colors = IronTheme.colors
    RuledRow(modifier, onClick = { onCheckedChange(!checked) }) {
        Column(Modifier.weight(1f)) {
            Text(title, style = IronTheme.type.body, color = colors.ink)
            secondary?.let { Text(it, style = IronTheme.type.secondary, color = colors.muted) }
        }
        IronSwitch(checked, onCheckedChange)
    }
}

/** The app's switch: `accent` track when on, `line` border when off. */
@Composable
fun IronSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = IronTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.muted,
            uncheckedTrackColor = colors.paper,
            uncheckedBorderColor = colors.line,
        ),
    )
}
