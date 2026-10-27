package com.ironquest.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.domain.nutrition.DayMark
import com.ironquest.app.domain.nutrition.MacroKind
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
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class FoodMonthUiState(
    val isLoading: Boolean = true,
    val month: YearMonth,
    val today: LocalDate,
    /** Every day of [month], coloured by protein status. */
    val marks: Map<LocalDate, DayMark> = emptyMap(),
    val stats: OverviewStats? = null,
) {
    val canGoForward: Boolean get() = month < YearMonth.from(today)
    val showsNoTarget: Boolean get() = DayMark.NO_TARGET in marks.values
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodMonthViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = MutableStateFlow(LocalDate.now(clock))
    private val month = MutableStateFlow(YearMonth.from(today.value))
    private var followCurrent = true

    val uiState: StateFlow<FoodMonthUiState> = combine(
        month.flatMapLatest { visibleMonth -> repository.observeHistory(visibleMonth.atDay(1), visibleMonth.atEndOfMonth()) },
        today,
    ) { history, currentDay -> buildState(history, currentDay) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FoodMonthUiState(month = month.value, today = today.value),
        )

    fun previousMonth() {
        followCurrent = false
        month.update { it.minusMonths(1) }
    }

    /** Same rule as Log: never past the current month. */
    fun nextMonth() {
        month.update { if (it < YearMonth.from(today.value)) it.plusMonths(1) else it }
        followCurrent = month.value == YearMonth.from(today.value)
    }

    fun refreshToday() {
        val now = LocalDate.now(clock)
        today.value = now
        if (followCurrent) month.value = YearMonth.from(now)
    }

    companion object {
        fun buildState(history: NutritionHistory, today: LocalDate): FoodMonthUiState {
            val days = NutritionOverviewCalculator.days(history, today)
            return FoodMonthUiState(
                isLoading = false,
                month = YearMonth.from(history.from),
                today = today,
                marks = days.associate { it.date to it.mark(MacroKind.PROTEIN) },
                stats = NutritionOverviewCalculator.stats(days, history, MacroKind.PROTEIN, today),
            )
        }
    }
}
