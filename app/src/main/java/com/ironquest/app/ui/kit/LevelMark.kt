package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.LevelPlacement

/** The character level in the style's position: corner "Level / 7", top-bar "LV 7" or pill "Level 7". */
@Composable
fun LevelMark(level: Int, modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val levelText = stringResource(R.string.celebration_level, level)
    val a11y = Modifier.clearAndSetSemantics { contentDescription = levelText }
    when (IronTheme.style.levelPlacement) {
        LevelPlacement.CORNER -> Column(modifier.then(a11y), horizontalAlignment = Alignment.End) {
            Text(stringResource(R.string.kit_level_label), style = type.secondary, color = colors.muted)
            Text(Formats.integer(level, currentLocale()), style = type.number, color = colors.ink)
        }
        LevelPlacement.TOP_BAR -> Text(stringResource(R.string.kit_level_short, level), style = type.label, color = colors.ink, modifier = modifier.then(a11y))
        LevelPlacement.PILL -> Text(
            levelText,
            style = type.label,
            color = colors.accent,
            modifier = modifier
                .then(a11y)
                .clip(IronTheme.shapes.chip)
                .background(colors.raised)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}
