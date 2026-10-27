package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import com.ironquest.app.ui.theme.SummaryVariant

data class SummaryCell(val label: String, val value: String)

/**
 * Three numbers side by side. Diary: 1.5 dp ink rule above, line rule below, serif numbers.
 * Bold: 3 dp ink rule, upper-case labels, huge numbers. Soft: three `raised` tiles.
 */
@Composable
fun SummaryStrip(cells: List<SummaryCell>, modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    val variant = IronTheme.style.summaryVariant
    if (variant == SummaryVariant.PILLS) {
        Row(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cells.forEach { cell ->
                Column(
                    Modifier.weight(1f).clip(IronTheme.shapes.tile).background(colors.raised).padding(12.dp),
                ) {
                    Text(cell.label, style = IronTheme.type.label, color = colors.muted)
                    Text(cell.value, style = IronTheme.type.number, color = colors.ink)
                }
            }
        }
        return
    }
    val top: Dp = if (variant == SummaryVariant.HEAVY_RULE) 3.dp else 1.5.dp
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawBehind {
                val topStroke = top.toPx()
                drawLine(colors.ink, Offset(0f, topStroke / 2), Offset(size.width, topStroke / 2), strokeWidth = topStroke)
                if (variant == SummaryVariant.THIN_RULE) {
                    val ruleY = size.height - 0.5.dp.toPx()
                    drawLine(colors.line, Offset(0f, ruleY), Offset(size.width, ruleY), strokeWidth = 1.dp.toPx())
                }
            }
            .padding(top = top),
    ) {
        cells.forEach { cell ->
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(IronTheme.style.cased(cell.label), style = IronTheme.type.label, color = colors.muted)
                Text(cell.value, style = IronTheme.type.number, color = colors.ink)
            }
        }
    }
}
