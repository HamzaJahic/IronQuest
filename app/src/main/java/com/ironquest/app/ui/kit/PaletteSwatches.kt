package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.domain.appearance.Palette
import com.ironquest.app.ui.theme.IronColors
import com.ironquest.app.ui.theme.IronTheme

/** Palette dots; the selected one gets an `ink` ring with a small gap. */
@Composable
fun PaletteSwatches(
    selected: Palette,
    label: (Palette) -> String,
    onSelect: (Palette) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IronTheme.colors
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Palette.entries.forEach { palette ->
            val isSelected = palette == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(palette) })
                    .semantics { contentDescription = label(palette) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .border(2.dp, if (isSelected) colors.ink else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(IronColors.swatch(palette))
                        .border(1.dp, colors.line, CircleShape),
                )
            }
        }
    }
}
