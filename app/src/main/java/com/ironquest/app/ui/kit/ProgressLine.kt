package com.ironquest.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme

/** Thin progress bar on a `line` track. [fraction] is clamped to 0..1. */
@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 5.dp) {
    val shape = IronTheme.shapes.bar
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(IronTheme.colors.line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(shape).background(color))
    }
}
