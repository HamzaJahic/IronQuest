package com.ironquest.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.model.DraftExercise
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutLogDraft
import com.ironquest.app.domain.progression.formatKg
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.catalog.templateNameText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.failedText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/** Raw text of one set row: weight (kg) and reps, or just minutes for cardio. */
data class LogSetUi(val weight: String = "", val amount: String = "") // not UI text: empty input defaults

data class LogExerciseUi(
    val exercise: ExerciseDefinition,
    val repRange: RepRange,
    /** What was done last time, shown as hints and used by "Same as last time". */
    val ghost: List<LoggedSet>,
    val sets: List<LogSetUi>,
) {
    val isCardio: Boolean get() = exercise.trackingType == TrackingType.DURATION
}

data class WorkoutLogEditorUiState(
    val isLoading: Boolean = true,
    /** The workout or its day no longer exists. */
    val notFound: Boolean = false,
    val isNew: Boolean = true,
    /** The day's name, "Deleted program" for a log whose program is gone; null when unknown. */
    val title: UiText? = null,
    val date: LocalDate? = null,
    val lastTime: LocalDate? = null,
    val exercises: List<LogExerciseUi> = emptyList(),
    /** The sets as loaded; [isDirty] compares against them. */
    val loadedSets: List<List<LogSetUi>> = emptyList(),
    val error: UiText? = null,
    val isSaving: Boolean = false,
) {
    val isDirty: Boolean get() = exercises.map { it.sets } != loadedSets
}

sealed interface WorkoutLogEvent {
    data object Saved : WorkoutLogEvent
    data object Deleted : WorkoutLogEvent
    data class Message(val text: UiText) : WorkoutLogEvent
}

/** Everything the workout log editor can ask the ViewModel to do. */
sealed interface WorkoutLogEditorAction {
    data class WeightChange(val exerciseId: String, val row: Int, val text: String) : WorkoutLogEditorAction
    data class AmountChange(val exerciseId: String, val row: Int, val text: String) : WorkoutLogEditorAction
    data class AddSet(val exerciseId: String) : WorkoutLogEditorAction
    data class RemoveSet(val exerciseId: String, val row: Int) : WorkoutLogEditorAction
    data class CopyLastTime(val exerciseId: String) : WorkoutLogEditorAction
    data object Save : WorkoutLogEditorAction
    data object Delete : WorkoutLogEditorAction
}

/** Result of reading one set row. */
sealed interface LogRow {
    data object Blank : LogRow
    data object Invalid : LogRow
    data class Ok(val set: LoggedSet) : LogRow
}

/**
 * Adds a finished workout to a past day (or today), or edits / deletes a stored one. Nothing is
 * written until Save, so abandoning the form leaves no half-finished session behind.
 */
@HiltViewModel
class WorkoutLogEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val programs: ProgramRepository,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    /** 0 = a new workout. */
    private val workoutId: Long = savedStateHandle[ARG_WORKOUT_ID] ?: 0L
    private val argTemplateId: String = savedStateHandle[ARG_TEMPLATE_ID] ?: ""
    private val argDate: Long = savedStateHandle[ARG_DATE] ?: 0L

    private val _uiState = MutableStateFlow(WorkoutLogEditorUiState(isNew = workoutId == 0L))
    val uiState: StateFlow<WorkoutLogEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkoutLogEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutLogEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    private var templateId = argTemplateId
    private var date: LocalDate = LocalDate.ofEpochDay(argDate)

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val log = if (workoutId != 0L) repository.findWorkoutLog(workoutId) else null
        if (workoutId != 0L && log == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        templateId = log?.templateId ?: argTemplateId
        date = log?.date ?: LocalDate.ofEpochDay(argDate)
        val template = programs.findTemplate(templateId)
        if (template == null && log == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        val ghost = repository.ghostFor(templateId, date, excludeWorkoutId = workoutId.takeIf { it != 0L })
        val logged = log?.exercises.orEmpty().associateBy { it.exerciseId }

        val fromTemplate = template?.exercises.orEmpty().map { te ->
            val saved = logged[te.exercise.id]
            LogExerciseUi(
                exercise = te.exercise,
                repRange = saved?.repRange ?: te.repRange,
                ghost = ghost?.setsByExercise?.get(te.exercise.id).orEmpty(),
                sets = when {
                    saved != null -> saved.sets.map { rowOf(te.exercise, it) }
                    log != null -> emptyList()
                    else -> List(te.targetSets) { LogSetUi() }
                },
            )
        }
        // Sets of exercises the day no longer has (edited program) stay editable.
        val extra = log?.exercises.orEmpty().filter { stored -> template?.exercises?.none { it.exercise.id == stored.exerciseId } != false }.map { stored ->
            val def = ExerciseDefinition(stored.exerciseId, stored.name, stored.stat, stored.trackingType)
            LogExerciseUi(def, stored.repRange, ghost?.setsByExercise?.get(stored.exerciseId).orEmpty(), stored.sets.map { rowOf(def, it) })
        }
        val exercises = fromTemplate + extra
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                title = template?.let(::templateNameText)
                    ?: log?.let { saved -> saved.templateName?.let { name -> templateNameText(saved.templateId, name) } ?: uiText(R.string.log_deleted_program) },
                date = date,
                lastTime = ghost?.date,
                exercises = exercises,
                loadedSets = exercises.map { exercise -> exercise.sets },
            )
        }
    }

    fun onAction(action: WorkoutLogEditorAction) {
        when (action) {
            is WorkoutLogEditorAction.WeightChange -> onWeightChange(action.exerciseId, action.row, action.text)
            is WorkoutLogEditorAction.AmountChange -> onAmountChange(action.exerciseId, action.row, action.text)
            is WorkoutLogEditorAction.AddSet -> addSet(action.exerciseId)
            is WorkoutLogEditorAction.RemoveSet -> removeSet(action.exerciseId, action.row)
            is WorkoutLogEditorAction.CopyLastTime -> copyLastTime(action.exerciseId)
            WorkoutLogEditorAction.Save -> save()
            WorkoutLogEditorAction.Delete -> delete()
        }
    }

    private fun onWeightChange(exerciseId: String, row: Int, text: String) {
        // "," is accepted as the decimal separator, like during a workout.
        val cleaned = text.replace(',', '.').filter { it.isDigit() || it == '.' }
        if (cleaned.count { it == '.' } > 1 || cleaned.length > 6) return
        updateRow(exerciseId, row) { it.copy(weight = cleaned) }
    }

    private fun onAmountChange(exerciseId: String, row: Int, text: String) {
        updateRow(exerciseId, row) { it.copy(amount = text.filter(Char::isDigit).take(3)) }
    }

    private fun addSet(exerciseId: String) = updateExercise(exerciseId) { it.copy(sets = it.sets + LogSetUi()) }

    private fun removeSet(exerciseId: String, row: Int) = updateExercise(exerciseId) { it.copy(sets = it.sets.filterIndexed { index, _ -> index != row }) }

    /** Replaces the exercise's rows with last session's sets. */
    private fun copyLastTime(exerciseId: String) = updateExercise(exerciseId) { ex ->
        if (ex.ghost.isEmpty()) ex else ex.copy(sets = ex.ghost.map { rowOf(ex.exercise, it) })
    }

    private fun updateRow(exerciseId: String, row: Int, change: (LogSetUi) -> LogSetUi) = updateExercise(exerciseId) { ex -> ex.copy(sets = ex.sets.mapIndexed { index, setRow -> if (index == row) change(setRow) else setRow }) }

    private fun updateExercise(exerciseId: String, change: (LogExerciseUi) -> LogExerciseUi) {
        _uiState.update { state ->
            state.copy(
                error = null,
                exercises = state.exercises.map { if (it.exercise.id == exerciseId) change(it) else it },
            )
        }
    }

    /** isSaving stays true after success so a second tap during the exit animation can't write twice. */
    private fun save() {
        val state = _uiState.value
        if (state.isSaving || state.isLoading || state.notFound) return
        val draft = when (val result = toDraft(workoutId.takeIf { it != 0L }, templateId, date, state.exercises)) {
            is DraftResult.Invalid -> {
                _uiState.update { it.copy(error = result.text) }
                return
            }
            is DraftResult.Ok -> result.draft
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                val goalTypes = settings.currentGoals().shownTypes
                writeMutex.withLock { repository.saveWorkoutLog(draft, goalTypes) }
                _events.send(WorkoutLogEvent.Saved)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(WorkoutLogEvent.Message(failedText(R.string.workout_log_save_failed, error)))
            }
        }
    }

    private fun delete() {
        if (workoutId == 0L || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                writeMutex.withLock { repository.deleteWorkout(workoutId) }
                _events.send(WorkoutLogEvent.Deleted)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(WorkoutLogEvent.Message(failedText(R.string.workout_log_delete_failed, error)))
            }
        }
    }

    sealed interface DraftResult {
        data class Ok(val draft: WorkoutLogDraft) : DraftResult
        data class Invalid(val text: UiText) : DraftResult
    }

    companion object {
        const val ARG_WORKOUT_ID = "workoutId" // not UI text: SavedStateHandle key
        const val ARG_TEMPLATE_ID = "templateId" // not UI text: SavedStateHandle key
        const val ARG_DATE = "date"
        private const val MAX_WEIGHT_KG = 1000.0

        fun rowOf(exercise: ExerciseDefinition, set: LoggedSet) = LogSetUi(
            weight = if (exercise.trackingType == TrackingType.DURATION) "" else set.weightKg.formatKg(),
            amount = (if (exercise.trackingType == TrackingType.DURATION) set.durationMinutes ?: 0 else set.reps).toString(),
        )

        /**
         * Reads one row. Empty weight and amount = [LogRow.Blank] (ignored). Reps/minutes are
         * required; an empty weight means bodyweight (0 kg).
         */
        fun parseRow(cardio: Boolean, row: LogSetUi): LogRow {
            if (row.weight.isBlank() && row.amount.isBlank()) return LogRow.Blank
            val amount = row.amount.toIntOrNull()?.takeIf { it > 0 } ?: return LogRow.Invalid
            if (cardio) return LogRow.Ok(LoggedSet(setIndex = 0, reps = 0, weightKg = 0.0, durationMinutes = amount))
            val weight = if (row.weight.isBlank()) 0.0 else row.weight.toDoubleOrNull()?.takeIf { it in 0.0..MAX_WEIGHT_KG } ?: return LogRow.Invalid
            return LogRow.Ok(LoggedSet(setIndex = 0, reps = amount, weightKg = weight))
        }

        /** Blank rows are dropped; the first unreadable one is named so the user can find it. */
        fun toDraft(workoutId: Long?, templateId: String, date: LocalDate, exercises: List<LogExerciseUi>): DraftResult {
            val drafts = mutableListOf<DraftExercise>()
            for (ex in exercises) {
                val sets = mutableListOf<LoggedSet>()
                ex.sets.forEachIndexed { index, row ->
                    when (val parsedRow = parseRow(ex.isCardio, row)) {
                        LogRow.Blank -> Unit
                        LogRow.Invalid -> return DraftResult.Invalid(
                            uiText(
                                if (ex.isCardio) R.string.workout_log_invalid_set_minutes else R.string.workout_log_invalid_set_reps,
                                index + 1,
                                exerciseNameText(ex.exercise),
                            ),
                        )
                        is LogRow.Ok -> sets += parsedRow.set
                    }
                }
                if (sets.isNotEmpty()) drafts += DraftExercise(ex.exercise, ex.repRange, sets)
            }
            if (drafts.isEmpty()) return DraftResult.Invalid(uiText(R.string.workout_log_no_sets))
            return DraftResult.Ok(WorkoutLogDraft(workoutId, templateId, date, drafts))
        }
    }
}
