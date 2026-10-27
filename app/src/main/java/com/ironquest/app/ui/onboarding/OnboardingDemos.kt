package com.ironquest.app.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.ui.kit.LevelMark
import com.ironquest.app.ui.theme.IronMotion
import com.ironquest.app.ui.theme.IronTheme

/**
 * A small looping demo for the steps a coach mark can't show: the welcome (no control yet), the
 * pager swipe and superset rounds (gestures across a page, not one control). Built from kit pieces
 * with sample data, so it matches the user's style and palette. Null when the step has none.
 * Animated only in the draw/layer phase; static on its final frame when animations are off.
 */
fun onboardingDemo(topic: OnboardingTopic, stepIndex: Int): (@Composable () -> Unit)? = when {
    topic == OnboardingTopic.WELCOME && stepIndex == 0 -> {
        { XpDemo() }
    }
    topic == OnboardingTopic.WORKOUT && stepIndex == 4 -> {
        { SwipeDemo() }
    }
    topic == OnboardingTopic.SUPERSET && stepIndex == 0 -> {
        { SupersetDemo() }
    }
    else -> null
}

/** The loop position 0..1, or a constant 1 (the final frame) when the system turned animations off. */
@Composable
private fun rememberDemoProgress(): State<Float> {
    val context = LocalContext.current
    val animate = remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    if (!animate) return remember { mutableFloatStateOf(1f) }
    return rememberInfiniteTransition(label = "demo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(IronMotion.DEMO_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        // not UI text: animation inspector label
        label = "demoProgress",
    )
}

/** Maps the loop [progress] onto 0..1 between [start] and [end] (fractions of the loop), eased. */
private fun phase(progress: Float, start: Float, end: Float): Float = FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

private val DemoModifier = Modifier.fillMaxWidth().heightIn(max = 160.dp).clearAndSetSemantics { }

/** A set earns XP: the level bar fills and "+10 XP" appears. */
@Composable
private fun XpDemo() {
    val colors = IronTheme.colors
    val progress = rememberDemoProgress()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(DemoModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LevelMark(level = 1)
        Box(
            Modifier
                .weight(1f)
                .height(6.dp)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(colors.line, cornerRadius = radius)
                    val fill = 0.3f + 0.5f * phase(progress.value, 0.15f, 0.55f)
                    val width = size.width * fill
                    drawRoundRect(colors.reward, topLeft = Offset(if (rtl) size.width - width else 0f, 0f), size = Size(width, size.height), cornerRadius = radius)
                },
        )
        Text(
            stringResource(R.string.common_xp_gain, 10),
            style = IronTheme.type.label,
            color = colors.reward,
            modifier = Modifier.graphicsLayer { alpha = phase(progress.value, 0.35f, 0.55f) },
        )
    }
}

/** Swipe to the next exercise: page one slides out, page two slides in, the dots follow. */
@Composable
private fun SwipeDemo() {
    val colors = IronTheme.colors
    val progress = rememberDemoProgress()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pageWidth = 200.dp
    Column(DemoModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(pageWidth).height(72.dp).clipToBounds()) {
            Row(
                Modifier.graphicsLayer {
                    val shift = pageWidth.toPx() * phase(progress.value, 0.3f, 0.6f)
                    translationX = if (rtl) shift else -shift
                },
            ) {
                DemoPage(stringResource(R.string.exercise_bench_press), pageWidth)
                DemoPage(stringResource(R.string.exercise_barbell_row), pageWidth)
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.size(width = 26.dp, height = 8.dp).drawBehind {
                val moved = phase(progress.value, 0.3f, 0.6f)
                val radius = 3.dp.toPx()
                val gap = size.width - 2 * radius * 2
                listOf(0, 1).forEach { index ->
                    val current = if (index == 0) moved < 0.5f else moved >= 0.5f
                    val x = radius + index * (2 * radius + gap)
                    drawCircle(if (current) colors.ink else colors.line, radius, Offset(if (rtl) size.width - x else x, size.height / 2))
                }
            },
        )
    }
}

@Composable
private fun DemoPage(name: String, width: Dp) {
    val colors = IronTheme.colors
    Box(Modifier.width(width).padding(horizontal = 4.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(IronTheme.shapes.sheet)
                .background(colors.paper)
                .border(1.dp, colors.line, IronTheme.shapes.sheet)
                .padding(12.dp),
        ) {
            Text(name, style = IronTheme.type.bodyStrong, color = colors.ink, maxLines = 1)
            // not UI text: sample weight, the format string carries the words
            val weight = "80"
            Text(stringResource(R.string.workout_set_weight_reps, weight, 8), style = IronTheme.type.secondary, color = colors.muted)
        }
    }
}

/** One set of each exercise, then the next round: the marker moves A1 → A2, then round 2 starts. */
@Composable
private fun SupersetDemo() {
    val colors = IronTheme.colors
    val progress = rememberDemoProgress()
    // not UI text: superset letters are never translated (WorkoutBlock.of)
    val letter = "A"
    Column(DemoModifier) {
        Box {
            Text(
                stringResource(R.string.workout_superset_round, 1, 3),
                style = IronTheme.type.label,
                color = colors.muted,
                modifier = Modifier.graphicsLayer { alpha = 1f - phase(progress.value, 0.62f, 0.72f) },
            )
            Text(
                stringResource(R.string.workout_superset_round, 2, 3),
                style = IronTheme.type.label,
                color = colors.muted,
                modifier = Modifier.graphicsLayer { alpha = phase(progress.value, 0.62f, 0.72f) },
            )
        }
        Spacer(Modifier.height(8.dp))
        listOf(R.string.exercise_bench_press, R.string.exercise_barbell_row).forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(width = 4.dp, height = 22.dp).graphicsLayer {
                        // Round 1: A1 then A2; round 2 starts back on A1.
                        val toSecond = phase(progress.value, 0.3f, 0.4f)
                        val backToFirst = phase(progress.value, 0.62f, 0.72f)
                        val onSecond = (toSecond - backToFirst).coerceIn(0f, 1f)
                        alpha = if (index == 0) 1f - onSecond else onSecond
                    }.background(colors.accent),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.workout_superset_member, letter, index + 1),
                    style = IronTheme.type.bodyStrong,
                    color = colors.ink,
                    modifier = Modifier.width(32.dp),
                )
                Text(stringResource(name), style = IronTheme.type.body, color = colors.ink, maxLines = 1)
            }
        }
    }
}
