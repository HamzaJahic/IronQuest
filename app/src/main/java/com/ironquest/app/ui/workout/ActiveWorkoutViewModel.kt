package com.ironquest.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalReached
import com.ironquest.app.domain.goals.GoalRules
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.WorkoutGoal
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.LogSetRequest
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.repository.AppSettingsRepository
import com.ironquest.app.domain.repository.ProgramRepository
import com.ironquest.app.domain.repository.WorkoutRepository
import com.ironquest.app.domain.workout.SessionRow
import com.ironquest.app.domain.workout.SetInput
import com.ironquest.app.domain.workout.WorkoutSession
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.catalog.templateNameText
import com.ironquest.app.ui.text.failedText
import com.ironquest.app.ui.text.uiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val programs: ProgramRepository,
    private val settings: AppSettingsRepository,
) : ViewModel() {

    private val templateId: String = checkNotNull(savedStateHandle[ARG_TEMPLATE_ID]) { "templateId missing" }

    private val _uiState = MutableStateFlow(ActiveWorkoutUiState())
    val uiState: StateFlow<ActiveWorkoutUiState> = _uiState.asStateFlow()

    private val _events = Channel<WorkoutEvent>(Channel.BUFFERED)
    val events: Flow<WorkoutEvent> = _events.receiveAsFlow()

    /** Created lazily by the repository on the first logged set. */
    private var workoutId: Long? = null

    /** Snapshot taken at load; also covers ghost sets beyond today's visible rows ("Add set"). */
    private var ghost: GhostSession? = null

    /** Today's workout goals, from the ghost snapshot (so they can't shift mid-session either). */
    private var goals: List<WorkoutGoal> = emptyList()

    /** Exercise ids whose goal is currently reached, so each one stamps once. */
    private val reachedGoals = mutableSetOf<String>()

    /** Serialises writes so two quick taps can't both create a workout row. */
    private val writeMutex = Mutex()

    init {
        viewModelScope.launch { load() }
    }

    fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.WeightChange -> onWeightChange(action.exerciseId, action.index, action.text)
            is WorkoutAction.AmountChange -> onAmountChange(action.exerciseId, action.index, action.text)
            is WorkoutAction.StepWeight -> stepWeight(action.exerciseId, action.index, action.up)
            is WorkoutAction.StepAmount -> stepAmount(action.exerciseId, action.index, action.up)
            is WorkoutAction.LogSet -> logSet(action.exerciseId, action.index)
            is WorkoutAction.EditSet -> editSet(action.exerciseId, action.index, action.weightText, action.amountText)
            is WorkoutAction.DeleteSet -> deleteSet(action.exerciseId, action.index)
            is WorkoutAction.AddSet -> addSet(action.exerciseId)
            is WorkoutAction.AddRound -> addRound(action.exerciseIds)
            WorkoutAction.Restart -> restartWorkout()
            WorkoutAction.Stop -> stopWorkout()
            WorkoutAction.Finish -> completeWorkout()
        }
    }

    private suspend fun load() {
        val template = programs.findTemplate(templateId)
        if (template == null) {
            _uiState.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        _uiState.update { it.copy(templateName = templateNameText(template)) }
        // Ghost is a snapshot: it must not shift under the user while they train.
        val ghost = repository.observeGhost(templateId).first().also { this.ghost = it }
        val resumed = repository.findInProgress(templateId)
        workoutId = resumed?.workoutId

        goals = GoalRules.forWorkout(template, ghost?.setsByExercise.orEmpty())
        // Goals a resumed session already reached don't stamp again.
        val resumedSets = resumed?.setsByExercise.orEmpty().mapValues { (_, sets) -> sets.map { saved -> saved.set } }
        goals.filter { goal -> GoalRules.isDone(goal, resumedSets[goal.exercise.exercise.id].orEmpty()) }
            .forEach { goal -> reachedGoals += goal.exercise.exercise.id }
        val goalSettings = settings.currentGoals()
        val shownGoals = goals.filter { goal -> goalSettings.isOn(goal.type, GoalSwitch.SHOW) }
            .associateBy { goal -> goal.exercise.exercise.id }

        val started = WorkoutSession.start(template, ghost, resumed)
        val session = started.copy(
            exercises = started.exercises.map { exercise ->
                exercise.copy(goal = shownGoals[exercise.id], goalReached = exercise.id in reachedGoals)
            },
        )
        val blocks = template.blocks
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                ghostDate = ghost?.date,
                session = session,
                blocks = blocks,
                initialPage = session.initialPage(blocks),
            )
        }
    }

    private fun onWeightChange(exerciseId: String, index: Int, text: String) {
        val cleaned = SetInput.cleanWeight(text) ?: return
        updateRow(exerciseId, index) { row -> row.copy(weightInput = cleaned) }
    }

    private fun onAmountChange(exerciseId: String, index: Int, text: String) {
        val cleaned = SetInput.cleanAmount(text)
        updateRow(exerciseId, index) { row -> row.copy(amountInput = cleaned) }
    }

    private fun addSet(exerciseId: String) = updateSession { session -> session.withAddedSet(exerciseId, ::ghostFor) }

    /** "+ Add round" on a superset: one more set for the group's longest exercises. */
    private fun addRound(exerciseIds: List<String>) = updateSession { session -> session.withAddedRound(exerciseIds, ::ghostFor) }

    private fun stepWeight(exerciseId: String, index: Int, up: Boolean) = updateSession { session -> session.update(exerciseId) { exercise -> exercise.withSteppedWeight(index, up) } }

    private fun stepAmount(exerciseId: String, index: Int, up: Boolean) = updateSession { session -> session.update(exerciseId) { exercise -> exercise.withSteppedAmount(index, up) } }

    /** Edit dialog of a logged set: new values, saved via [logSet] (updateSet, no XP change). */
    private fun editSet(exerciseId: String, index: Int, weightText: String, amountText: String) {
        onWeightChange(exerciseId, index, weightText)
        onAmountChange(exerciseId, index, amountText)
        logSet(exerciseId, index)
    }

    private fun logSet(exerciseId: String, index: Int) {
        val exercise = currentSession().exercise(exerciseId) ?: return
        var row = exercise.rows.getOrNull(index) ?: return
        if (row.isSaving) return
        // The big number shows the suggested / last-time weight while the input is empty: log that.
        if (!exercise.isCardio && row.weightInput.isBlank() && !row.isLogged) {
            row = row.copy(weightInput = exercise.shownWeight(row))
            updateRow(exerciseId, index) { it.copy(weightInput = row.weightInput) }
        }

        val set = exercise.parse(row) ?: run {
            val hint = uiText(if (exercise.isCardio) R.string.workout_hint_minutes else R.string.workout_hint_weight_reps)
            _events.trySend(WorkoutEvent.Message(hint))
            return
        }
        if (row.isLogged && row.persisted == set) return

        updateRow(exerciseId, index) { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                writeMutex.withLock {
                    val existingId = currentSession().exercise(exerciseId)?.rows?.get(index)?.persistedId
                    if (existingId == null) {
                        insertSet(exercise.definition, row.ghost, set)
                    } else {
                        repository.updateSet(existingId, set)
                        updateRow(exerciseId, index) { it.copy(persisted = set, isSaving = false) }
                        checkGoal(exerciseId)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateRow(exerciseId, index) { it.copy(isSaving = false) }
                _events.send(WorkoutEvent.Message(failedText(R.string.workout_save_set_failed, error)))
            }
        }
    }

    private suspend fun insertSet(definition: TemplateExercise, ghost: LoggedSet?, set: LoggedSet) {
        val exerciseId = definition.exercise.id
        val result = repository.logSet(
            LogSetRequest(templateId = templateId, workoutId = workoutId, exercise = definition, set = set, ghost = ghost),
        )
        workoutId = result.workoutId
        updateSession { session ->
            session.update(exerciseId) { exercise -> exercise.withLoggedSet(set, result.setId, result.xp.total) }
        }
        result.levelUp?.let { levelUp -> _events.send(WorkoutEvent.LevelUp(levelUp)) }
        checkGoal(exerciseId)
    }

    /**
     * Live goal check after a set of [exerciseId] was logged or edited: stamps a shown goal the
     * first time it is reached (when Celebrate is on). An edit that undoes it lets it stamp again.
     */
    private suspend fun checkGoal(exerciseId: String) {
        val goal = goals.firstOrNull { it.exercise.exercise.id == exerciseId } ?: return
        val sets = currentSession().exercise(exerciseId)?.loggedSets.orEmpty()
        if (!GoalRules.isDone(goal, sets)) {
            reachedGoals -= exerciseId
            setGoalReached(exerciseId, reached = false)
            return
        }
        if (!reachedGoals.add(exerciseId)) return
        setGoalReached(exerciseId, reached = true)
        val goalSettings = settings.currentGoals()
        if (goalSettings.isOn(goal.type, GoalSwitch.SHOW) && goalSettings.isOn(goal.type, GoalSwitch.CELEBRATE)) {
            _events.send(WorkoutEvent.GoalReached(goal.type, exerciseNameText(goal.exercise.exercise), goal.bonusXp))
        }
    }

    /** Removes a logged set and its XP; the row opens again for logging (an added extra set goes away). */
    private fun deleteSet(exerciseId: String, index: Int) {
        val row = currentSession().exercise(exerciseId)?.rows?.getOrNull(index) ?: return
        val setId = row.persistedId ?: return
        if (row.isSaving) return
        updateRow(exerciseId, index) { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                writeMutex.withLock {
                    val emptied = repository.deleteSet(setId)
                    if (emptied) workoutId = null
                    updateSession { session -> session.update(exerciseId) { exercise -> exercise.withoutSet(index) } }
                }
                checkGoal(exerciseId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                updateRow(exerciseId, index) { it.copy(isSaving = false) }
                _events.send(WorkoutEvent.Message(failedText(R.string.workout_delete_set_failed, error)))
            }
        }
    }

    /** Deletes the session so far (sets and their XP) and starts this workout again from the first page. */
    private fun restartWorkout() = resetSession {
        reachedGoals.clear()
        load()
        _uiState.update { it.copy(sessionKey = it.sessionKey + 1) }
    }

    /** Deletes the session so far (sets and their XP) and leaves the screen. */
    private fun stopWorkout() = resetSession { _events.send(WorkoutEvent.Stopped) }

    private fun resetSession(then: suspend () -> Unit) {
        val state = _uiState.value
        if (state.isResetting || state.isCompleting || state.victory != null) return
        _uiState.update { it.copy(isResetting = true) }
        viewModelScope.launch {
            try {
                writeMutex.withLock {
                    workoutId?.let { id -> repository.deleteWorkout(id) }
                    workoutId = null
                }
                then()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _events.send(WorkoutEvent.Message(failedText(R.string.workout_delete_workout_failed, error)))
            } finally {
                _uiState.update { it.copy(isResetting = false) }
            }
        }
    }

    private fun completeWorkout() {
        val id = workoutId ?: return
        if (!_uiState.value.canComplete) return
        _uiState.update { it.copy(isCompleting = true) }
        viewModelScope.launch {
            try {
                // Only goal types shown on Today are paid.
                val goalTypes = settings.currentGoals().shownTypes
                val result = writeMutex.withLock { repository.completeWorkout(id, goalTypes) }
                _uiState.update { current ->
                    current.copy(
                        isCompleting = false,
                        victory = VictoryUi(
                            setXp = result.totalXpEarned - result.bonusXp - result.goalXp,
                            bonusXp = result.bonusXp,
                            totalXp = result.totalXpEarned,
                            levelUp = result.levelUp,
                            goals = result.goals,
                            sets = current.loggedSets,
                            durationMillis = result.durationMillis,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _uiState.update { it.copy(isCompleting = false) }
                _events.send(WorkoutEvent.Message(failedText(R.string.workout_finish_failed, error)))
            }
        }
    }

    private fun ghostFor(exerciseId: String, setIndex: Int): LoggedSet? = ghost?.ghostFor(exerciseId, setIndex)

    private fun currentSession(): WorkoutSession = _uiState.value.session

    private fun updateSession(transform: (WorkoutSession) -> WorkoutSession) = _uiState.update { it.copy(session = transform(it.session)) }

    private fun updateRow(exerciseId: String, index: Int, transform: (SessionRow) -> SessionRow) = updateSession { session -> session.updateRow(exerciseId, index, transform) }

    private fun setGoalReached(exerciseId: String, reached: Boolean) = updateSession { session -> session.update(exerciseId) { exercise -> exercise.copy(goalReached = reached) } }

    companion object {
        const val ARG_TEMPLATE_ID = "templateId" // not UI text: SavedStateHandle key
    }
}
