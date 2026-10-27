package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.DayOverview
import com.ironquest.app.domain.nutrition.MacroKind
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.NutritionHistory
import com.ironquest.app.domain.nutrition.NutritionOverviewCalculator
import com.ironquest.app.domain.nutrition.OverviewStats
import com.ironquest.app.domain.repository.NutritionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekBarUi(
    val date: LocalDate,
    val value: Double,
    /** 0..1 of the chart height. */
    val fraction: Float,
    val mark: DayMark,
    val isFuture: Boolean,
)

data class WeekChart(
    val bars: List<WeekBarUi>,
    /** The gold line: the latest target in the week for the selected macro. */
    val referenceTarget: Double?,
    val targetFraction: Float?,
)

data class FoodWeekUiState(
    val isLoading: Boolean = true,
    val weekStart: LocalDate,
    val today: LocalDate,
    val macro: MacroKind = MacroKind.PROTEIN,
    val chart: WeekChart = WeekChart(bars = emptyList(), referenceTarget = null, targetFraction = null),
    val stats: OverviewStats? = null,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val canGoForward: Boolean get() = weekEnd < today
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodWeekViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Re-read on resume (refreshToday) so the view rolls over at midnight. */
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val weekStart = MutableStateFlow(startOfWeek(today.value))
    private val macro = MutableStateFlow(MacroKind.PROTEIN)

    /** True while the user hasn't left the current week. */
    private var followCurrent = true

    val uiState: StateFlow<FoodWeekUiState> = combine(
        weekStart.flatMapLatest { start -> repository.observeHistory(start, start.plusDays(6)) },
        today,
        macro,
    ) { history, t, m -> buildState(history, t, m) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoodWeekUiState(weekStart = weekStart.value, today = today.value),
        )

    fun previousWeek() {
        followCurrent = false
        weekStart.update { it.minusWeeks(1) }
    }

    fun nextWeek() {
        weekStart.update { if (it.plusWeeks(1) <= today.value) it.plusWeeks(1) else it }
        followCurrent = weekStart.value == startOfWeek(today.value)
    }

    fun selectMacro(kind: MacroKind) {
        macro.value = kind
    }

    /** Called from a LifecycleResumeEffect, like FoodDayViewModel.refreshToday. */
    fun refreshToday() {
        val now = LocalDate.now(clock)
        today.value = now
        if (followCurrent) weekStart.value = startOfWeek(now)
    }

    companion object {
        /** Bars leave this much headroom above the larger of target and intake. */
        const val HEADROOM = 1.15

        private fun MacroKind.targetOf(targets: MacroTargets): Double? = when (this) {
            MacroKind.PROTEIN -> targets.proteinG
            MacroKind.CARBS -> targets.carbsG
            MacroKind.FAT -> targets.fatG
            MacroKind.FIBER -> targets.fiberG
            MacroKind.KCAL -> targets.kcal
        }

        fun startOfWeek(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        fun buildState(
            history: NutritionHistory,
            today: LocalDate,
            macro: MacroKind,
        ): FoodWeekUiState {
            val days = NutritionOverviewCalculator.days(history, today)
            return FoodWeekUiState(
                isLoading = false,
                weekStart = history.from,
                today = today,
                macro = macro,
                chart = chart(days, macro, liveTarget = if (today in history.from..history.to) history.liveTargets?.let { macro.targetOf(it) } else null),
                stats = NutritionOverviewCalculator.stats(days, history, macro, today),
            )
        }

        /** Pure chart maths so it can be unit-tested; the composable only draws it. */
        fun chart(days: List<DayOverview>, kind: MacroKind, liveTarget: Double? = null): WeekChart {
            val values = days.map { day -> day.summary?.let { kind.of(it).eaten } ?: 0.0 }
            // The live plan target wins when today is in the week; otherwise the latest snapshot target.
            val target = liveTarget?.takeIf { it > 0.0 }
                ?: days.asReversed()
                    .firstNotNullOfOrNull { day -> day.summary?.let { kind.of(it).target }?.takeIf { it > 0.0 } }
            val top = maxOf(target ?: 0.0, values.maxOrNull() ?: 0.0) * HEADROOM
            val scale = if (top > 0.0) top else 1.0
            return WeekChart(
                bars = days.mapIndexed { index, day ->
                    WeekBarUi(day.date, values[index], (values[index] / scale).toFloat().coerceIn(0f, 1f), day.mark(kind), day.isFuture)
                },
                referenceTarget = target,
                targetFraction = target?.let { (it / scale).toFloat() },
            )
        }
    }
}
