package com.ironquest.app.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme

/**
 * Short points as a list instead of a paragraph: a `muted` dot, then text that wraps under
 * itself (hanging indent). Keep each item to one idea.
 */
@Composable
fun BulletList(
    items: List<AnnotatedString>,
    modifier: Modifier = Modifier,
    style: TextStyle = IronTheme.type.body,
    color: Color = IronTheme.colors.ink,
    spacing: Int = 6,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
        items.forEach { item ->
            Row {
                Text("•", style = style, color = IronTheme.colors.muted, modifier = Modifier.width(18.dp))
                Text(item, style = style, color = color, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Plain-text convenience for [BulletList]. */
@Composable
fun BulletList(
    vararg items: String,
    modifier: Modifier = Modifier,
    style: TextStyle = IronTheme.type.body,
    color: Color = IronTheme.colors.ink,
) = BulletList(items.map { AnnotatedString(it) }, modifier, style, color)
