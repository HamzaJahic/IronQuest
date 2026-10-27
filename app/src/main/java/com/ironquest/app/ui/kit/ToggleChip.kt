package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme

/** An on/off filter chip (tags, "Archived"): `line` border; when on, `ink` fill with `paper` text. */
@Composable
fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    val shape = IronTheme.shapes.chip
    Text(
        text,
        style = IronTheme.type.label.let { if (selected) it.copy(fontWeight = FontWeight.Bold) else it },
        color = if (selected) colors.paper else colors.ink,
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.ink else colors.paper)
            .border(1.dp, if (selected) colors.ink else colors.line, shape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .heightIn(min = 32.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
