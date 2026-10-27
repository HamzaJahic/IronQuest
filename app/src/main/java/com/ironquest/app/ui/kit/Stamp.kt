package com.ironquest.app.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironquest.app.ui.theme.IronTheme
import kotlinx.coroutines.delay

/** How long a stamp stays before [Stamp]'s `onSettled` fires. */
const val STAMP_HOLD_MILLIS = 1_500L

/**
 * Celebration stamp ("Level 8", "Limit break"): `reward` text in a 2 dp `reward` border, rotated
 * −6°, scaling 1.15 → 1 in 250 ms. After ~1.5 s [onSettled] fires; the caller then replaces it with
 * a one-line entry ("Level 8 reached"). Keyed on [text], so a new text plays again.
 */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, onSettled: () -> Unit = {}) {
    val colors = IronTheme.colors
    val settled by rememberUpdatedState(onSettled)
    val scale = remember(text) { Animatable(1.15f) }
    LaunchedEffect(text) {
        scale.animateTo(1f, tween(durationMillis = 250))
        delay(STAMP_HOLD_MILLIS)
        settled()
    }
    Text(
        text,
        style = IronTheme.type.sectionHead,
        color = colors.reward,
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .graphicsLayer {
                rotationZ = -6f
                scaleX = scale.value
                scaleY = scale.value
            }
            .border(2.dp, colors.reward, IronTheme.shapes.chip)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
