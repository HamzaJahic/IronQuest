package com.ironquest.app.ui.kit

import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.ironquest.app.ui.theme.IronTheme

/**
 * A full-screen scrim with a hole around [hole] (screen coordinates) and [card] beside it: below
 * the hole when it fits, else above, else centred. With no hole (or one scrolled off screen) the
 * card is centred on a plain scrim. It is its own window, so touches and TalkBack stay inside and
 * nothing underneath can be pressed by accident. Back press calls [onDismiss].
 */
@Composable
fun Spotlight(hole: Rect?, onDismiss: () -> Unit, card: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false),
    ) {
        // The scrim is ours: no platform dim, and the window covers the whole screen, system bars included.
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val colors = IronTheme.colors
        SideEffect {
            if (window == null) return@SideEffect
            window.setDimAmount(0f)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // A dialog window sits between opaque system bars by default; this one draws under them.
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION") // still honoured below API 35, where bars aren't transparent by default
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    fitInsetsTypes = 0
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
        // In dark palettes `ink` is light, so the darker of ink/paper dims in both modes.
        val scrim = if (colors.isDark) colors.paper.copy(alpha = 0.72f) else colors.ink.copy(alpha = 0.55f)
        // The hole takes the style's tile corners (square in Bold, round in Soft).
        val holeShape = IronTheme.shapes.tile
        var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
        val local = origin?.let { coordinates ->
            hole?.takeIf { rect -> rect.width > 0f && rect.height > 0f }?.let { rect -> Rect(coordinates.screenToLocal(rect.topLeft), rect.size) }
        }
        val insets = WindowInsets.safeDrawing
        Layout(
            content = card,
            modifier = Modifier
                .onGloballyPositioned { coordinates -> origin = coordinates }
                .drawWithCache {
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        if (local != null) {
                            val hole = local.inflate(8.dp.toPx())
                            addPath(Path().apply { addOutline(holeShape.createOutline(hole.size, layoutDirection, this@drawWithCache)) }, hole.topLeft)
                        }
                    }
                    onDrawBehind { drawPath(path, scrim) }
                },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val margin = 16.dp.roundToPx()
            val gap = 16.dp.roundToPx()
            val top = insets.getTop(this) + margin
            val bottom = height - insets.getBottom(this) - margin
            val left = insets.getLeft(this, layoutDirection) + margin
            val right = width - insets.getRight(this, layoutDirection) - margin
            val cardWidth = (right - left).coerceAtMost(480.dp.roundToPx()).coerceAtLeast(0)
            val placeable = measurables.first().measure(Constraints(minWidth = cardWidth, maxWidth = cardWidth, maxHeight = (bottom - top).coerceAtLeast(0)))
            val below = local?.let { rect -> rect.bottom.toInt() + gap }
            val above = local?.let { rect -> rect.top.toInt() - gap - placeable.height }
            val y = when {
                below != null && below + placeable.height <= bottom -> below
                above != null && above >= top -> above
                else -> top + (bottom - top - placeable.height) / 2
            }
            layout(width, height) { placeable.place(left + (right - left - cardWidth) / 2, y) }
        }
    }
}
