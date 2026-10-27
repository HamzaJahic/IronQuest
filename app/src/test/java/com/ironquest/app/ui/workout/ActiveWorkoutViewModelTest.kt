package com.ironquest.app.ui.workout

import androidx.lifecycle.SavedStateHandle
import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.InProgressSession
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.PersistedSet
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.progression.OverloadSuggestion
import com.ironquest.app.domain.workout.RowStatus
import com.ironquest.app.fakes.FakeAppSettingsRepository
import com.ironquest.app.fakes.FakeProgramRepository
import com.ironquest.app.fakes.FakeWorkoutRepository
import com.ironquest.app.ui.text.UiText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {

    private val repo = FakeWorkoutRepository()
    private val settings = FakeAppSettingsRepository()

    private fun benchSets(weight: Double, vararg reps: Int) = reps.mapIndexed { index, repCount -> LoggedSet(index, repCount, weight) }

    private fun viewModel(): ActiveWorkoutViewModel = ActiveWorkoutViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ActiveWorkoutViewModel.ARG_TEMPLATE_ID to "UPPER_1")),
        repository = repo,
        programs = FakeProgramRepository(),
        settings = settings,
    )

    private fun withMain(block: suspend () -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            runTest { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun seedGhost(benchReps: IntArray, weight: Double = 100.0) {
        repo.ghosts.value = mapOf(
            "UPPER_1" to GhostSession(
                workoutId = 1,
                date = LocalDate.of(2026, 9, 22),
                setsByExercise = mapOf("bench_press" to benchSets(weight, *benchReps)),
            ),
        )
    }

    private fun ActiveWorkoutViewModel.bench() = uiState.value.exercises.first { it.id == "bench_press" }

    @Test
    fun `ghost mode exposes last session per set and the engine suggestion`() = withMain {
        seedGhost(intArrayOf(8, 8, 8, 8))
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(false, state.isLoading)
        assertEquals(LocalDate.of(2026, 9, 22), state.ghostDate)
        assertEquals(0, state.initialPage)
        assertEquals(LoggedSet(2, 8, 100.0), vm.bench().rows[2].ghost)
        assertTrue(vm.bench().suggestion is OverloadSuggestion.LevelUp)
        // Exercises without history get no ghost.
        assertEquals(null, state.exercises.first { it.id == "barbell_row" }.rows[0].ghost)
    }

    @Test
    fun `logging a set persists it, awards xp with ghost bonus and carries weight and reps forward`() = withMain {
        seedGhost(intArrayOf(8, 8, 8, 8))
        val vm = viewModel()

        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "102,5")) // comma decimal from a European keyboard
        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "8"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))

        val row = vm.bench().rows[0]
        assertTrue(row.isLogged)
        assertEquals(LoggedSet(0, 8, 102.5), row.persisted)
        assertEquals(15, vm.uiState.value.sessionXp) // 10 base + 5 ghost slayer
        assertEquals("102.5", vm.bench().rows[1].weightInput)
        assertEquals("8", vm.bench().rows[1].amountInput)
        assertEquals(1, repo.logRequests.size)
        assertEquals(null, repo.logRequests.single().workoutId)

        // Second set reuses the workout created by the first.
        vm.onAction(WorkoutAction.AmountChange("bench_press", 1, "7"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 1))
        assertEquals(100L, repo.logRequests[1].workoutId)
        assertEquals(25, vm.uiState.value.sessionXp) // 7 < 8 reps at heavier weight doesn't slay
    }

    @Test
    fun `onboarding facts follow logged sets, the current page and the finish summary`() = withMain {
        val vm = viewModel()
        assertEquals(emptySet<OnboardingFact>(), vm.uiState.value.onboardingFacts(currentPage = 0))

        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "8"))
        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))
        assertEquals(setOf(OnboardingFact.SET_LOGGED), vm.uiState.value.onboardingFacts(currentPage = 0))

        val pair = ProgramCatalog.templates.first().exercises.take(2)
        val superset = vm.uiState.value.copy(blocks = listOf(WorkoutBlock.Superset("A", pair)))
        assertTrue(OnboardingFact.SUPERSET_VISIBLE in superset.onboardingFacts(currentPage = 0))
        assertTrue(OnboardingFact.SUPERSET_VISIBLE !in superset.onboardingFacts(currentPage = 1))

        val finished = vm.uiState.value.copy(victory = VictoryUi(setXp = 10, bonusXp = 100, totalXp = 110, levelUp = null))
        assertTrue(OnboardingFact.BLOCKED in finished.onboardingFacts(currentPage = 0))
    }

    @Test
    fun `the next set starts from the previous set, not the first`() = withMain {
        val vm = viewModel()
        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "8"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))

        vm.onAction(WorkoutAction.WeightChange("bench_press", 1, "105"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 1, "6"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 1))

        assertEquals("105", vm.bench().rows[2].weightInput)
        assertEquals("6", vm.bench().rows[2].amountInput)
        assertEquals("105", vm.bench().rows[3].weightInput)
    }

    @Test
    fun `invalid rows are not persisted`() = withMain {
        val vm = viewModel()
        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0)) // no reps
        assertEquals(0, repo.logRequests.size)
        assertEquals(false, vm.bench().rows[0].isSaving)
    }

    @Test
    fun `editing a logged set updates it without re-awarding xp`() = withMain {
        val vm = viewModel()
        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "6"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))
        assertEquals(RowStatus.LOGGED, vm.bench().statusOf(vm.bench().rows[0]))

        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "7"))
        assertEquals(RowStatus.DIRTY, vm.bench().statusOf(vm.bench().rows[0]))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))

        assertEquals(1, repo.logRequests.size)
        assertEquals(LoggedSet(0, 7, 100.0), repo.updates.single().second)
        assertEquals(10, vm.uiState.value.sessionXp)
        assertEquals(RowStatus.LOGGED, vm.bench().statusOf(vm.bench().rows[0]))
    }

    @Test
    fun `a failed save leaves the row editable`() = withMain {
        val vm = viewModel()
        repo.failNextLog = true
        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "6"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))
        assertEquals(false, vm.bench().rows[0].isLogged)
        assertEquals(false, vm.bench().rows[0].isSaving)
    }

    @Test
    fun `completing the workout pays the bonus and shows victory`() = withMain {
        val vm = viewModel()
        assertEquals(false, vm.uiState.value.canComplete)

        vm.onAction(WorkoutAction.WeightChange("bench_press", 0, "100"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 0, "6"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))
        vm.onAction(WorkoutAction.Finish)

        val victory = checkNotNull(vm.uiState.value.victory)
        assertEquals(10, victory.setXp)
        assertEquals(100, victory.bonusXp)
        assertEquals(110, victory.totalXp)
        assertEquals(listOf(100L), repo.completed)
        assertEquals(false, vm.uiState.value.canComplete)
    }

    @Test
    fun `an unfinished session is resumed`() = withMain {
        repo.inProgress = InProgressSession(
            workoutId = 42,
            templateId = "UPPER_1",
            setsByExercise = mapOf("bench_press" to listOf(PersistedSet(7, LoggedSet(0, 6, 95.0), xpAwarded = 10))),
        )
        val vm = viewModel()

        val row = vm.bench().rows[0]
        assertEquals(7L, row.persistedId)
        assertEquals("95", row.weightInput)
        assertEquals("6", row.amountInput)
        assertEquals(10, vm.uiState.value.sessionXp)

        vm.onAction(WorkoutAction.WeightChange("bench_press", 1, "95"))
        vm.onAction(WorkoutAction.AmountChange("bench_press", 1, "6"))
        vm.onAction(WorkoutAction.LogSet("bench_press", 1))
        assertEquals(42L, repo.logRequests.single().workoutId)
    }

    @Test
    fun `add set extends the exercise with ghost and last weight`() = withMain {
        seedGhost(intArrayOf(8, 8, 8, 8, 6))
        val vm = viewModel()
        vm.onAction(WorkoutAction.WeightChange("bench_press", 3, "100"))
        vm.onAction(WorkoutAction.AddSet("bench_press"))

        val added = vm.bench().rows.last()
        assertEquals(4, added.index)
        assertEquals("100", added.weightInput)
        assertEquals(LoggedSet(4, 6, 100.0), added.ghost)
    }

    @Test
    fun `reaching a shown goal stamps once and completion pays only shown goal types`() = withMain {
        seedGhost(intArrayOf(8, 8, 8, 8)) // Limit break: 102.5 kg
        settings.setGoalSwitch(GoalType.BEAT_LAST_TIME, GoalSwitch.SHOW, false)
        val vm = viewModel()
        val events = mutableListOf<WorkoutEvent>()
        val job = CoroutineScope(Dispatchers.Main).launch { vm.events.collect { events += it } }
        repeat(4) { index ->
            vm.onAction(WorkoutAction.WeightChange("bench_press", index, "102.5"))
            vm.onAction(WorkoutAction.AmountChange("bench_press", index, "5"))
            vm.onAction(WorkoutAction.LogSet("bench_press", index))
        }
        vm.onAction(WorkoutAction.LogSet("bench_press", 3)) // unchanged re-tap: no second stamp
        val stamps = events.filterIsInstance<WorkoutEvent.GoalReached>()
        assertEquals(listOf(WorkoutEvent.GoalReached(GoalType.LIMIT_BREAK, UiText.Res(R.string.exercise_bench_press), 25)), stamps)

        vm.onAction(WorkoutAction.Finish)
        assertEquals(setOf(GoalType.LIMIT_BREAK, GoalType.FOOD), repo.completedGoalTypes.single())
        job.cancel()
    }

    private fun ActiveWorkoutViewModel.logBench(index: Int, weight: String = "100", reps: String = "8") {
        onAction(WorkoutAction.WeightChange("bench_press", index, weight))
        onAction(WorkoutAction.AmountChange("bench_press", index, reps))
        onAction(WorkoutAction.LogSet("bench_press", index))
    }

    @Test
    fun `deleting a set reopens its row and takes back its xp`() = withMain {
        val vm = viewModel()
        vm.logBench(0)
        vm.logBench(1)
        assertEquals(20, vm.uiState.value.sessionXp)

        vm.onAction(WorkoutAction.DeleteSet("bench_press", 0))
        val row = vm.bench().rows[0]
        assertEquals(false, row.isLogged)
        assertEquals("8", row.amountInput) // typed in again, ready to re-log
        assertEquals(0, vm.bench().currentRow?.index)
        assertEquals(10, vm.uiState.value.sessionXp)
        assertEquals(listOf(1L), repo.deletedSets)

        // The session still exists: the next set goes into the same workout.
        vm.onAction(WorkoutAction.LogSet("bench_press", 0))
        assertEquals(100L, repo.logRequests.last().workoutId)
    }

    @Test
    fun `deleting the last set forgets the emptied session and drops an extra row`() = withMain {
        val vm = viewModel()
        val target = vm.bench().definition.targetSets
        vm.onAction(WorkoutAction.AddSet("bench_press"))
        vm.logBench(target)
        assertEquals(target + 1, vm.bench().rows.size)

        vm.onAction(WorkoutAction.DeleteSet("bench_press", target))
        assertEquals(target, vm.bench().rows.size)
        assertEquals(0, vm.uiState.value.loggedSets)

        // The repository removed the empty session, so the next set starts a new one.
        vm.logBench(0)
        assertEquals(null, repo.logRequests.last().workoutId)
    }

    @Test
    fun `restart deletes the session and starts over`() = withMain {
        val vm = viewModel()
        vm.logBench(0)
        vm.onAction(WorkoutAction.Restart)

        assertEquals(listOf(100L), repo.deleted)
        val state = vm.uiState.value
        assertEquals(0, state.loggedSets)
        assertEquals(0, state.sessionXp)
        assertEquals(1, state.sessionKey)
        assertEquals(false, state.isResetting)
        vm.logBench(0)
        assertEquals(null, repo.logRequests.last().workoutId)
    }

    @Test
    fun `stop deletes the session and leaves the screen`() = withMain {
        val vm = viewModel()
        val events = mutableListOf<WorkoutEvent>()
        val job = CoroutineScope(Dispatchers.Main).launch { vm.events.collect { events += it } }
        vm.logBench(0)
        vm.onAction(WorkoutAction.Stop)

        assertEquals(listOf(100L), repo.deleted)
        assertEquals(listOf<WorkoutEvent>(WorkoutEvent.Stopped), events)
        job.cancel()
    }
}
