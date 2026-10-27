package com.ironquest.app.ui.imports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportIssue
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.importing.ImportParse
import com.ironquest.app.domain.importing.ImportProblem
import com.ironquest.app.domain.importing.MealCsv
import com.ironquest.app.domain.importing.MealImportResult
import com.ironquest.app.domain.importing.MealImportUseCase
import com.ironquest.app.domain.importing.SlotImport
import com.ironquest.app.domain.importing.WorkoutCsv
import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.repository.ProgramException
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.catalog.templateNameText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiPlural
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
import javax.inject.Inject
import kotlin.math.roundToInt

/** A successfully parsed file, waiting for the user to press IMPORT. */
sealed interface ParsedImport {
    data class Days(val days: List<DayImport>) : ParsedImport
    data class Meals(val meals: List<MealDraft>) : ParsedImport
    data class Plan(val slots: List<SlotImport>) : ParsedImport
}

/** A workout day that exercises can be imported into. */
data class DayTarget(val templateId: String, val label: UiText)

data class ImportUiState(
    val kind: ImportKind,
    /** Name for a new program / meal plan. */
    val name: String = "",
    /** Imported programs (built-in ones can't be changed). */
    val programs: List<Program> = emptyList(),
    /** WORKOUT_DAY(S): program to add to; null = create a new program. */
    val targetProgramId: Long? = null,
    /** EXERCISE(S): day to add to. */
    val targetTemplateId: String? = null,
    /** The loaded file's name, or "Pasted text". */
    val fileName: UiText? = null,
    val parsed: ParsedImport? = null,
    val issues: List<ImportIssue> = emptyList(),
    val isImporting: Boolean = false,
) {
    val dayTargets: List<DayTarget>
        get() = programs.flatMap { program -> program.templates.map { DayTarget(it.id, joinedText(listOf(UiText.Verbatim(program.name), templateNameText(it)), R.string.common_dot_separator)) } }

    /** Program and meal plan imports (and day imports into a new program) need a name. */
    val needsName: Boolean
        get() = kind == ImportKind.PROGRAM || kind == ImportKind.MEAL_PLAN || (isDayImport && targetProgramId == null)

    val isDayImport: Boolean get() = kind == ImportKind.WORKOUT_DAY || kind == ImportKind.WORKOUT_DAYS
    val isExerciseImport: Boolean get() = kind == ImportKind.EXERCISE || kind == ImportKind.EXERCISES

    /** Why IMPORT is disabled, or null when it's ready. */
    val blocker: UiText?
        get() = when {
            parsed == null -> null
            isExerciseImport && dayTargets.isEmpty() -> uiText(R.string.import_blocker_built_in_only)
            isExerciseImport && targetTemplateId == null -> uiText(R.string.import_blocker_pick_day)
            needsName && name.isBlank() -> uiText(R.string.import_blocker_name)
            else -> null
        }

    val canImport: Boolean get() = parsed != null && blocker == null && !isImporting

    /** Short summary of what IMPORT will create; [formatTime] shows a time of day (minutes after midnight) the way the device does. */
    fun previewLines(formatTime: (Int) -> String): List<UiText> = when (val content = parsed) {
        null -> emptyList()
        is ParsedImport.Days -> content.days.map { day ->
            val exercises = joinedText(
                day.exercises.map { exercise ->
                    uiText(
                        R.string.import_preview_exercise,
                        exerciseNameText(exercise.definition),
                        exercise.targetSets,
                        uiText(R.string.common_range, exercise.repRange.min, exercise.repRange.max),
                    )
                },
                R.string.common_list_separator,
            )
            if (isExerciseImport) exercises else uiText(R.string.import_preview_day, UiText.Verbatim(day.name), exercises)
        }
        is ParsedImport.Meals -> content.meals.map { meal ->
            uiText(R.string.import_preview_meal, UiText.Verbatim(meal.name), meal.macros.proteinG.fmt(), meal.macros.carbsG.fmt(), meal.macros.fatG.fmt())
        }
        is ParsedImport.Plan -> content.slots.map { slot ->
            val time = slot.timeOfDayMin?.let { UiText.Verbatim(formatTime(it)) }
            val label = slot.label?.let { UiText.Verbatim(it) }
            val head = when {
                time != null && label != null -> uiText(R.string.import_preview_slot_time_label, time, label)
                time != null -> time
                label != null -> label
                else -> UiText.Verbatim("")
            }
            uiText(R.string.import_preview_slot, head, UiText.Verbatim(slot.meal.name))
        }
    }
}

private fun Double.fmt(): Int = roundToInt()

sealed interface ImportEvent {
    data class Message(val text: UiText) : ImportEvent

    /** Import written; leave the screen and show [message] on the previous one. */
    data class Done(val message: UiText) : ImportEvent
}

/** Everything the import screen can ask the ViewModel to do. */
sealed interface ImportAction {
    data class KindChange(val kind: ImportKind) : ImportAction
    data class NameChange(val name: String) : ImportAction
    data class TargetProgram(val id: Long?) : ImportAction
    data class TargetDay(val templateId: String) : ImportAction
    data class CsvLoaded(val text: String, val fileName: String?) : ImportAction
    data class LoadFailed(val problem: ImportProblem) : ImportAction
    data object Import : ImportAction
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programs: ProgramRepository,
    private val meals: MealImportUseCase,
) : ViewModel() {

    private val initialKind = runCatching { ImportKind.valueOf(checkNotNull(savedStateHandle[ARG_KIND])) }.getOrDefault(ImportKind.PROGRAM)

    private val _uiState = MutableStateFlow(ImportUiState(kind = initialKind))
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    private val writeMutex = Mutex()

    /** Last loaded text, re-parsed when the kind changes. */
    private var csvText: String? = null

    init {
        viewModelScope.launch {
            programs.observePrograms().collect { all ->
                val imported = all.filter { !it.isBuiltIn }
                _uiState.update { current ->
                    val active = imported.firstOrNull { it.isActive }
                    current.copy(
                        programs = imported,
                        targetProgramId = current.targetProgramId?.takeIf { id -> imported.any { it.id == id } }
                            ?: if (current.programs.isEmpty()) active?.id else null,
                        targetTemplateId = current.targetTemplateId?.takeIf { id -> imported.any { program -> program.templates.any { it.id == id } } }
                            ?: (active ?: imported.firstOrNull())?.templates?.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun onAction(action: ImportAction) {
        when (action) {
            is ImportAction.KindChange -> onKindChange(action.kind)
            is ImportAction.NameChange -> onNameChange(action.name)
            is ImportAction.TargetProgram -> onTargetProgram(action.id)
            is ImportAction.TargetDay -> onTargetDay(action.templateId)
            is ImportAction.CsvLoaded -> onCsvLoaded(action.text, action.fileName)
            is ImportAction.LoadFailed -> onLoadFailed(action.problem)
            ImportAction.Import -> import()
        }
    }

    private fun onKindChange(kind: ImportKind) {
        if (kind == _uiState.value.kind || _uiState.value.isImporting) return
        _uiState.update { it.copy(kind = kind) }
        csvText?.let(::parse)
    }

    private fun onNameChange(name: String) = _uiState.update { it.copy(name = name.take(60)) }

    private fun onTargetProgram(id: Long?) = _uiState.update { it.copy(targetProgramId = id) }

    private fun onTargetDay(templateId: String) = _uiState.update { it.copy(targetTemplateId = templateId) }

    /** [fileName] (e.g. "push_pull_legs.csv") also becomes the default name. */
    private fun onCsvLoaded(text: String, fileName: String?) {
        if (_uiState.value.isImporting) return
        csvText = text
        _uiState.update { current ->
            val defaultName = fileName?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.take(60)
            current.copy(fileName = fileName?.let { UiText.Verbatim(it) } ?: uiText(R.string.import_pasted_text), name = current.name.ifBlank { defaultName.orEmpty() })
        }
        parse(text)
    }

    /** The file couldn't be read at all (permission, binary file…). */
    private fun onLoadFailed(problem: ImportProblem) {
        csvText = null
        _uiState.update { it.copy(parsed = null, issues = listOf(ImportIssue(line = null, problem))) }
    }

    private fun parse(text: String) {
        val kind = _uiState.value.kind
        val result: ImportParse<ParsedImport> = try {
            when (kind) {
                ImportKind.PROGRAM, ImportKind.WORKOUT_DAYS -> WorkoutCsv.parse(text, requireDay = true).map(ParsedImport::Days)
                ImportKind.WORKOUT_DAY -> WorkoutCsv.parse(text, requireDay = true, expectedDays = 1).map(ParsedImport::Days)
                ImportKind.EXERCISE -> WorkoutCsv.parse(text, requireDay = false, expectedExercises = 1).map(ParsedImport::Days)
                ImportKind.EXERCISES -> WorkoutCsv.parse(text, requireDay = false).map(ParsedImport::Days)
                ImportKind.MEAL -> MealCsv.parseMeals(text, expected = 1).map(ParsedImport::Meals)
                ImportKind.MEALS -> MealCsv.parseMeals(text).map(ParsedImport::Meals)
                ImportKind.MEAL_PLAN -> MealCsv.parsePlan(text).map(ParsedImport::Plan)
            }
        } catch (error: Exception) {
            // Parsers report problems as issues; this is only a safety net so a bad file can't crash the app.
            ImportParse.Failed(listOf(ImportIssue(line = null, ImportProblem.CouldntRead(error.javaClass.simpleName))))
        }
        _uiState.update { current ->
            when (result) {
                is ImportParse.Ok -> current.copy(parsed = result.value, issues = emptyList())
                is ImportParse.Failed -> current.copy(parsed = null, issues = result.issues)
            }
        }
    }

    private fun import() {
        val state = _uiState.value
        val parsed = state.parsed ?: return
        if (!state.canImport) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            try {
                val message = writeMutex.withLock { write(state, parsed) }
                // Stay "importing" so a second tap during the exit can't import twice.
                _events.send(ImportEvent.Done(message))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { current ->
                    current.copy(isImporting = false, issues = listOf(ImportIssue(line = null, error.toProblem())))
                }
            }
        }
    }

    private fun Exception.toProblem(): ImportProblem = when {
        this is ProgramException -> ImportProblem.ProgramRefused(problem)
        message != null -> ImportProblem.Other(checkNotNull(message))
        else -> ImportProblem.ImportFailed(javaClass.simpleName)
    }

    private suspend fun write(state: ImportUiState, parsed: ParsedImport): UiText = when (parsed) {
        is ParsedImport.Days -> when {
            state.kind == ImportKind.PROGRAM || (state.isDayImport && state.targetProgramId == null) -> {
                programs.importProgram(state.name.trim(), parsed.days)
                uiText(R.string.import_done_program, UiText.Verbatim(state.name.trim()), uiPlural(R.plurals.import_days_count, parsed.days.size))
            }
            state.isDayImport -> {
                programs.addDays(checkNotNull(state.targetProgramId), parsed.days)
                uiText(R.string.import_done_days, uiPlural(R.plurals.import_workout_days_count, parsed.days.size))
            }
            else -> {
                val exercises = parsed.days.flatMap { it.exercises }
                programs.addExercises(checkNotNull(state.targetTemplateId), exercises)
                uiText(R.string.import_done_exercises, uiPlural(R.plurals.import_exercises_count, exercises.size))
            }
        }
        is ParsedImport.Meals -> meals.importMeals(parsed.meals).summary()
        is ParsedImport.Plan -> {
            val result = meals.importPlan(state.name.trim(), parsed.slots)
            uiText(R.string.import_done_plan, UiText.Verbatim(state.name.trim()), result.summary())
        }
    }

    private fun MealImportResult.summary(): UiText = if (reused > 0) {
        uiText(R.string.import_meals_summary_reused, uiPlural(R.plurals.import_new_meals_count, created), uiPlural(R.plurals.import_existing_meals_count, reused))
    } else {
        uiPlural(R.plurals.import_new_meals_count, created)
    }

    companion object {
        const val ARG_KIND = "kind"
    }
}

private inline fun <T, R> ImportParse<T>.map(f: (T) -> R): ImportParse<R> = when (this) {
    is ImportParse.Ok -> ImportParse.Ok(f(value))
    is ImportParse.Failed -> this
}
