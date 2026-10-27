package com.ironquest.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.domain.model.CompletedWorkoutLog
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** Selected day and every completed workout on it (empty = rest day). */
data class DayDetailUi(val date: LocalDate, val log: List<CompletedWorkoutLog>)

data class LogUiState(
    val month: YearMonth,
    val today: LocalDate,
    val completedDays: Set<LocalDate> = emptySet(),
    val selected: DayDetailUi? = null,
    /** Days of the active program, offered by "Add workout". */
    val templates: List<WorkoutTemplate> = emptyList(),
) {
    val canGoForward: Boolean get() = month < YearMonth.from(today)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    programs: ProgramRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    private val completedDays = month.flatMapLatest { repository.observeCompletedDays(it) }

    private val selectedDetail = selectedDate.flatMapLatest { date ->
        if (date == null) {
            flowOf(null)
        } else {
            repository.observeDayLog(date).map { DayDetailUi(date, it) }
        }
    }

    private val templates = programs.observeActiveProgram().map { it?.templates.orEmpty() }

    val uiState: StateFlow<LogUiState> = combine(month, completedDays, selectedDetail, templates) { m, days, detail, days2 ->
        LogUiState(month = m, today = today, completedDays = days, selected = detail, templates = days2)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogUiState(month = YearMonth.from(today), today = today),
    )

    fun previousMonth() = month.update { it.minusMonths(1) }

    fun nextMonth() = month.update { if (it < YearMonth.from(today)) it.plusMonths(1) else it }

    /** Future days can't hold history, so they're ignored. */
    fun selectDay(date: LocalDate) {
        if (!date.isAfter(today)) selectedDate.value = date
    }

    fun dismissDay() {
        selectedDate.value = null
    }

    /** Takes the workout's XP back and removes it; the day sheet updates through the log flow. */
    fun deleteWorkout(workoutId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteWorkout(workoutId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // The log flow keeps showing the workout; nothing else to undo.
            }
        }
    }
}
