package com.ironquest.app.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.MacroKind
import com.ironquest.app.domain.nutrition.OverviewStats
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.kit.DateStepper
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.kit.SegmentedControl
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme
import java.time.LocalDate
import java.time.format.TextStyle

class FoodWeekActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSelectMacro: (MacroKind) -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
)

@Composable
fun FoodWeekPane(
    contentPadding: PaddingValues,
    top: @Composable () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: FoodWeekViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    val actions = remember(viewModel, onOpenDay) {
        FoodWeekActions(viewModel::previousWeek, viewModel::nextWeek, viewModel::selectMacro, onOpenDay)
    }
    FoodWeekContent(
        state = state,
        contentPadding = contentPadding,
        top = top,
        actions = actions,
    )
}

@Composable
private fun FoodWeekContent(
    state: FoodWeekUiState,
    contentPadding: PaddingValues,
    top: @Composable () -> Unit,
    actions: FoodWeekActions,
) {
    val colors = IronTheme.colors
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = topInset + 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DateStepper(weekLabel(state.weekStart), state.canGoForward, actions.onPrevious, actions.onNext, unit = stringResource(R.string.common_period_week))
        top()
        val shortLabels = MacroKind.entries.associateWith { stringResource(it.shortRes()) }
        SegmentedControl(
            options = MacroKind.entries,
            selected = state.macro,
            label = { shortLabels.getValue(it) },
            onSelect = actions.onSelectMacro,
            modifier = Modifier.fillMaxWidth().onboardingAnchor(OnboardingTarget.FOOD_WEEK_MACRO),
        )
        if (state.isLoading) return@Column
        WeekBarChart(state, actions.onOpenDay)
        MarkLegend(showNoTarget = state.chart.bars.any { it.mark == DayMark.NO_TARGET })
        state.stats?.let { WeekSummary(it, state.macro) }
        Text(stringResource(R.string.food_week_tap_hint), style = IronTheme.type.secondary, color = colors.muted)
    }
}

/** 7 Box bars against a dashed `ink` target line drawn on an overlay. No chart library. */
@Composable
private fun WeekBarChart(state: FoodWeekUiState, onOpenDay: (LocalDate) -> Unit) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val unit = stringResource(state.macro.unitRes())
    val locale = currentLocale()
    val datePattern = stringResource(R.string.food_date_cd_pattern)
    val barShape = IronTheme.shapes.bar
    Column(Modifier.padding(top = 8.dp).onboardingAnchor(OnboardingTarget.FOOD_WEEK_BARS)) {
        Text(
            state.chart.referenceTarget?.let { stringResource(R.string.food_week_target, it.wholeText(locale), unit) }
                ?: stringResource(R.string.food_week_no_target, stringResource(state.macro.labelRes()).lowercase(locale)),
            style = type.secondary,
            color = colors.muted,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().height(140.dp)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                state.chart.bars.forEach { bar ->
                    val markLabel = stringResource(bar.mark.labelRes())
                    val dateText = Formats.date(bar.date, datePattern, locale)
                    val description = if (bar.isFuture) {
                        stringResource(R.string.food_cell_upcoming_cd, dateText)
                    } else {
                        stringResource(R.string.food_week_bar_cd, dateText, bar.value.amountText(locale), unit, markLabel)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(enabled = !bar.isFuture, role = Role.Button) { onOpenDay(bar.date) }
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (!bar.isFuture) {
                            val height = if (bar.mark == DayMark.NO_DATA) Modifier.height(4.dp) else Modifier.fillMaxHeight(bar.fraction.coerceAtLeast(0.02f))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .then(height)
                                    .clip(barShape)
                                    .background(bar.mark.fillColor(colors)),
                            )
                        }
                    }
                }
            }
            state.chart.targetFraction?.let { fraction ->
                // Drawn on top; Spacer has no pointer input, so taps still reach the bars.
                Spacer(
                    Modifier.matchParentSize().drawWithCache {
                        val targetY = size.height * (1f - fraction)
                        val stroke = 1.5.dp.toPx()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                        onDrawBehind { drawLine(colors.ink, Offset(0f, targetY), Offset(size.width, targetY), strokeWidth = stroke, pathEffect = dash) }
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.chart.bars.forEach { bar ->
                val isToday = bar.date == state.today
                Text(
                    bar.date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale) + "\n" + Formats.integer(bar.date.dayOfMonth, locale),
                    style = if (isToday) type.label.copy(fontWeight = FontWeight.Bold) else type.label,
                    color = if (isToday) colors.ink else colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WeekSummary(stats: OverviewStats, macro: MacroKind) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    val locale = currentLocale()
    SectionHeader(stringResource(R.string.food_week_average_header))
    RowGroup {
        RuledRow {
            Text(stats.average?.let { averageLine(it) } ?: stringResource(R.string.food_week_nothing_logged), style = type.body, color = colors.ink)
        }
        RuledRow {
            Text(stringResource(R.string.food_week_on_target, stringResource(macro.labelRes()).lowercase(locale)), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.food_of_days_count, stats.loggedDays, stats.onTargetDays, stats.loggedDays), style = type.secondary, color = colors.muted)
        }
        RuledRow {
            Text(stringResource(R.string.food_water), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text(stats.avgWaterMl?.let { litres(it) } ?: stringResource(R.string.common_no_value), style = type.secondary, color = colors.muted)
        }
        stats.supplements.forEach { supplement ->
            RuledRow {
                Text(supplement.name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                Text(pluralStringResource(R.plurals.food_of_days_count, supplement.ofDays, supplement.takenDays, supplement.ofDays), style = type.secondary, color = colors.muted)
            }
        }
    }
}

/** "5 Oct – 11 Oct 2026" */
@Composable
private fun weekLabel(start: LocalDate): String {
    val locale = currentLocale()
    return stringResource(
        R.string.food_week_range,
        Formats.date(start, stringResource(R.string.food_week_start_pattern), locale),
        Formats.date(start.plusDays(6), stringResource(R.string.food_week_end_pattern), locale),
    )
}
