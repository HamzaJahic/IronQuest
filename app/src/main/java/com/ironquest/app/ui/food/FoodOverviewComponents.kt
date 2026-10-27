package com.ironquest.app.ui.food

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.theme.IronColors
import com.ironquest.app.ui.theme.IronTheme
import kotlin.math.roundToInt

/** The Food tab's top-level switch. */
enum class FoodMode(@StringRes val labelRes: Int) {
    DAY(R.string.food_mode_day),
    WEEK(R.string.food_mode_week),
    MONTH(R.string.food_mode_month),
}

/** Spec mapping: under `muted` 60 %, on target `accent`, over `hold`, no target `line`, no data `line` 40 %. */
fun DayMark.fillColor(colors: IronColors): Color = when (this) {
    DayMark.ON_TARGET -> colors.accent
    DayMark.UNDER -> colors.muted.copy(alpha = 0.6f)
    DayMark.OVER -> colors.hold
    DayMark.NO_TARGET -> colors.line
    DayMark.NO_DATA -> colors.line.copy(alpha = 0.4f)
}

/** Text drawn on top of [fillColor]. */
fun DayMark.contentColor(colors: IronColors): Color = when (this) {
    DayMark.ON_TARGET -> colors.onAccent
    DayMark.OVER -> colors.paper
    DayMark.UNDER, DayMark.NO_TARGET -> colors.ink
    DayMark.NO_DATA -> colors.muted
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkLegend(showNoTarget: Boolean, modifier: Modifier = Modifier) {
    val colors = IronTheme.colors
    val marks = listOfNotNull(
        DayMark.ON_TARGET,
        DayMark.UNDER,
        DayMark.OVER,
        DayMark.NO_TARGET.takeIf { showNoTarget },
        DayMark.NO_DATA,
    )
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        marks.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(IronTheme.shapes.bar).background(mark.fillColor(colors)))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(mark.labelRes()), style = IronTheme.type.secondary, color = colors.muted)
            }
        }
    }
}

/** "P 158 · C 190 · F 55 · Fib 24 · 1850 kcal" */
@Composable
fun averageLine(macros: Macros): String {
    val locale = currentLocale()
    return stringResource(
        R.string.food_average_line,
        macros.proteinG.wholeText(locale),
        macros.carbsG.wholeText(locale),
        macros.fatG.wholeText(locale),
        macros.fiberG.wholeText(locale),
        macros.effectiveKcal.wholeText(locale),
    )
}

/** 2350 → "2.4 L" */
@Composable
fun litres(ml: Int): String = stringResource(R.string.food_amount_litres, Formats.decimal((ml / 100.0).roundToInt() / 10.0, currentLocale()))
