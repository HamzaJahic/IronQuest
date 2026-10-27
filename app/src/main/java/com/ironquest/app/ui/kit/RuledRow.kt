package com.ironquest.app.ui.kit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.ListTreatment

/**
 * One list row. Diary: dashed `line` rule below. Bold: solid 1 dp `line` rule above. Soft: no
 * rule, padded for a `raised` tile (put Soft rows in a [RowGroup]).
 */
@Composable
fun RuledRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val line = IronTheme.colors.line
    val treatment = IronTheme.style.listTreatment
    val rule = when (treatment) {
        // drawWithCache: the PathEffect and pixel sizes are built once per size, not per frame.
        ListTreatment.RULED_DASHED -> Modifier.drawWithCache {
            val ruleY = size.height - 0.5.dp.toPx()
            val stroke = 1.dp.toPx()
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            onDrawBehind { drawLine(line, Offset(0f, ruleY), Offset(size.width, ruleY), strokeWidth = stroke, pathEffect = dash) }
        }
        ListTreatment.RULED_SOLID -> Modifier.drawWithCache {
            val ruleY = 0.5.dp.toPx()
            val stroke = 1.dp.toPx()
            onDrawBehind { drawLine(line, Offset(0f, ruleY), Offset(size.width, ruleY), strokeWidth = stroke) }
        }
        ListTreatment.TILES -> Modifier
    }
    val horizontal = if (treatment == ListTreatment.TILES) 16.dp else 0.dp
    Row(
        modifier
            .fillMaxWidth()
            .then(rule)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = horizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
