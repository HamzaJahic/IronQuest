package com.ironquest.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.OverviewStats
import com.ironquest.app.domain.onboarding.OnboardingTarget
import com.ironquest.app.ui.format.DatePatterns
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import com.ironquest.app.ui.format.formatDate
import com.ironquest.app.ui.kit.DateStepper
import com.ironquest.app.ui.kit.DayCell
import com.ironquest.app.ui.kit.MonthGrid
import com.ironquest.app.ui.kit.RowGroup
import com.ironquest.app.ui.kit.RuledRow
import com.ironquest.app.ui.kit.SectionHeader
import com.ironquest.app.ui.onboarding.onboardingAnchor
import com.ironquest.app.ui.theme.IronTheme
import java.time.LocalDate
import java.time.YearMonth

class FoodMonthActions(
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onOpenDay: (LocalDate) -> Unit,
)

@Composable
fun FoodMonthPane(
    contentPadding: PaddingValues,
    top: @Composable () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: FoodMonthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    val actions = remember(viewModel, onOpenDay) {
        FoodMonthActions(viewModel::previousMonth, viewModel::nextMonth, onOpenDay)
    }
    FoodMonthContent(
        state = state,
        contentPadding = contentPadding,
        top = top,
        actions = actions,
    )
}

@Composable
private fun FoodMonthContent(
    state: FoodMonthUiState,
    contentPadding: PaddingValues,
    top: @Composable () -> Unit,
    actions: FoodMonthActions,
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
        DateStepper(
            formatDate(state.month.atDay(1), DatePatterns.MONTH_YEAR),
            state.canGoForward,
            actions.onPrevious,
            actions.onNext,
            unit = stringResource(R.string.common_period_month),
        )
        top()
        if (state.isLoading) return@Column
        FoodMonthGrid(state.month, state.today, state.marks, actions.onOpenDay)
        MarkLegend(showNoTarget = state.showsNoTarget)
        state.stats?.let { MonthSummary(it) }
        Text(stringResource(R.string.food_month_hint), style = IronTheme.type.secondary, color = colors.muted)
    }
}

@Composable
private fun FoodMonthGrid(month: YearMonth, today: LocalDate, marks: Map<LocalDate, DayMark>, onOpenDay: (LocalDate) -> Unit) {
    val colors = IronTheme.colors
    val locale = currentLocale()
    val datePattern = stringResource(R.string.food_date_cd_pattern)
    MonthGrid(month, Modifier.padding(top = 4.dp).onboardingAnchor(OnboardingTarget.FOOD_MONTH_GRID)) { date, cellModifier ->
        val mark = marks[date] ?: DayMark.NO_DATA
        val isFuture = date.isAfter(today)
        val markLabel = stringResource(mark.labelRes())
        DayCell(
            date = date,
            fill = if (isFuture) colors.paper else mark.fillColor(colors),
            contentColor = if (isFuture) colors.line else mark.contentColor(colors),
            isToday = date == today,
            isFuture = isFuture,
            description = Formats.date(date, datePattern, locale).let { dateText ->
                if (isFuture) stringResource(R.string.food_cell_upcoming_cd, dateText) else stringResource(R.string.food_month_cell_cd, dateText, markLabel)
            },
            onClick = { onOpenDay(date) },
            modifier = cellModifier,
        )
    }
}

@Composable
private fun MonthSummary(stats: OverviewStats) {
    val colors = IronTheme.colors
    val type = IronTheme.type
    SectionHeader(stringResource(R.string.food_month_summary_header))
    RowGroup {
        RuledRow {
            Text(stringResource(R.string.food_month_on_target), style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
            Text(pluralStringResource(R.plurals.food_of_logged_days_count, stats.loggedDays, stats.onTargetDays, stats.loggedDays), style = type.secondary, color = colors.muted)
        }
        stats.supplements.forEach { supplement ->
            RuledRow {
                Text(supplement.name, style = type.body, color = colors.ink, modifier = Modifier.weight(1f))
                Text(
                    pluralStringResource(R.plurals.food_day_count, supplement.streak, supplement.streak),
                    style = type.secondary,
                    color = colors.muted,
                )
            }
        }
    }
}
