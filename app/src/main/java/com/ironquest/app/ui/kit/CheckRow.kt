package com.ironquest.app.ui.kit

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme

/** The diary check box: `ink` outline, a ✓ when checked. */
@Composable
fun CheckBox(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = IronTheme.colors
    val tint = if (enabled) colors.ink else colors.muted
    Box(
        modifier.size(20.dp).border(1.5.dp, tint, IronTheme.shapes.bar),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", style = IronTheme.type.bodyStrong, color = tint)
    }
}

/**
 * A checklist row ([RuledRow]): the box toggles via [onToggle]; [content] sits to its right.
 * [label] is what TalkBack reads for the box.
 */
@Composable
fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    RuledRow(modifier) {
        Box(
            Modifier
                .size(44.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            CheckBox(checked, enabled = enabled)
        }
        content()
    }
}
