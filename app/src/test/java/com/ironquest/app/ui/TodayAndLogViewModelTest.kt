package com.ironquest.app.ui

import com.ironquest.app.R
import com.ironquest.app.domain.goals.GoalSwitch
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.LastCompletion
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealPlan
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.onboarding.OnboardingFact
import com.ironquest.app.domain.program.NextWorkoutChoice
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.fakes.FakeAppSettingsRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.fakes.FakeProgramRepository
import com.ironquest.app.fakes.FakeWorkoutRepository
import com.ironquest.app.ui.food.MutableClock
import com.ironquest.app.ui.log.LogViewModel
import com.ironquest.app.ui.text.uiText
import com.ironquest.app.ui.today.DayUi
import com.ironquest.app.ui.today.GoalUi
import com.ironquest.app.ui.today.NextLine
import com.ironquest.app.ui.today.TodayUiState
import com.ironquest.app.ui.today.TodayViewModel
import com.ironquest.app.ui.today.onboardingFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TodayAndLogViewModelTest {

    private val repo = FakeWorkoutRepository()
    private val nutrition = FakeNutritionRepository()
    private val settings = FakeAppSettingsRepository()

    // Tuesday 29 Sep 2026
    private val day = LocalDate.of(2026, 9, 29)
    private val clock = Clock.fixed(day.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private val oats = Macros(35.0, 75.0, 9.0, 9.0)
    private val chicken = Macros(45.0, 60.0, 12.0, 3.0)
    private val breakfast = PlanSlot(21, "Breakfast", 1, "Oats", oats, 450, null) // 07:30
    private val snack = PlanSlot(23, "Snack", 3, "Shake", oats, 540, null) // 09:00
    private val lunch = PlanSlot(22, "Lunch", 2, "Chicken rice", chicken, 780, null) // 13:00

    private fun cut(vararg slots: PlanSlot) = MealPlan(10, "Cut", isActive = true, targets = MacroTargets(proteinG = 180.0), slots = slots.toList())

    private fun today(at: Clock = clock, food: NutritionRepository = nutrition) = TodayViewModel(repo, food, at, FakeProgramRepository(), settings)

    private fun withMain(block: suspend () -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            runTest { block() }
        } finally {
            Locale.setDefault(previousLocale)
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `today picks the rotation, notes the main lift and counts this week`() = withMain {
        repo.ghosts.value = mapOf(
            "UPPER_1" to GhostSession(1, LocalDate.of(2026, 9, 22), mapOf("bench_press" to (0..3).map { LoggedSet(it, 8, 100.0) })),
        )
        // Mon 28 Sep counts; Sun 27 Sep is last week.
        repo.completedDays.value = setOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 27))
        val vm = today()

        val state = vm.uiState.first { !it.isLoading }
        assertEquals("UPPER_1", state.main?.template?.id) // nothing completed → start of rotation
        assertEquals("Barbell Bench Press 102.5 kg, up 2.5 from last time", EnglishText.resolve(state.mainNote))
        assertEquals(3, state.otherDays.size)
        assertEquals(1, state.weekDone)
        assertEquals(4, state.weekTarget)
    }

    @Test
    fun `do next makes a day the main one until a workout is completed after it`() = withMain {
        val vm = today()
        assertEquals("UPPER_1", vm.uiState.first { !it.isLoading }.main?.template?.id)

        vm.doNext("UPPER_2")
        val chosen = vm.uiState.first { it.main?.template?.id == "UPPER_2" }
        assertTrue(chosen.mainIsChosen)
        assertEquals(listOf("UPPER_1", "LOWER_1", "LOWER_2"), chosen.otherDays.map { it.template.id })

        // Upper 2 finished after the choice: the rotation continues from it.
        repo.lastCompletion.value = LastCompletion("UPPER_2", clock.millis() + 1)
        val after = vm.uiState.first { it.main?.template?.id == "LOWER_2" }
        assertFalse(after.mainIsChosen)
    }

    @Test
    fun `back to the rotation and do next on the rotation's day clear the choice`() = withMain {
        val vm = today()
        vm.uiState.first { !it.isLoading }
        vm.doNext("LOWER_2")
        vm.uiState.first { it.mainIsChosen }
        vm.backToRotation()
        assertEquals("UPPER_1", vm.uiState.first { !it.mainIsChosen }.main?.template?.id)
        assertNull(settings.nextChoice.value)

        vm.doNext("UPPER_1")
        assertNull(settings.nextChoice.value)
    }

    @Test
    fun `a completion before the choice keeps it`() = withMain {
        repo.lastCompletion.value = LastCompletion("UPPER_1", clock.millis() - 1)
        settings.nextChoice.value = NextWorkoutChoice("UPPER_1", clock.millis())
        val state = today().uiState.first { !it.isLoading }
        assertEquals("UPPER_1", state.main?.template?.id)
        assertTrue(state.mainIsChosen)
        assertEquals("LOWER_1", state.rotationTemplateId)
    }

    @Test
    fun `today lists shown goals and hides a type when its switch is off`() = withMain {
        repo.ghosts.value = mapOf(
            "UPPER_1" to GhostSession(
                1,
                LocalDate.of(2026, 9, 22),
                mapOf(
                    "bench_press" to (0..3).map { LoggedSet(it, 8, 100.0) }, // top of 5–8 → Limit break
                    "barbell_row" to (0..3).map { LoggedSet(it, 7, 70.0) }, // inside the range → Beat last time
                ),
            ),
        )
        val vm = today()
        var state = vm.uiState.first { !it.isLoading }
        assertEquals(listOf(GoalType.LIMIT_BREAK, GoalType.BEAT_LAST_TIME), state.goals.map { it.type })
        assertEquals("Barbell Bench Press: move to 102.5 kg", EnglishText.resolve(state.goals[0].title))
        assertEquals(uiText(R.string.common_xp_gain, 25), state.goals[0].hint)
        assertEquals("+25 XP", EnglishText.resolve(state.goals[0].hint))
        assertEquals(uiText(R.string.common_xp_gain, 10), state.goals[1].hint)
        assertTrue(state.exercises.first { it.id == "bench_press" }.limitBreak)

        settings.setGoalSwitch(GoalType.LIMIT_BREAK, GoalSwitch.SHOW, false)
        state = vm.uiState.first { it.goals.size == 1 }
        assertEquals(GoalType.BEAT_LAST_TIME, state.goals.single().type)
        assertFalse(state.exercises.any { it.limitBreak })
    }

    @Test
    fun `today shows the food goal from the active plan`() = withMain {
        nutrition.plans.value = listOf(cut(breakfast))
        nutrition.logs.value = mapOf(day to listOf(MealLogEntry(1, 21, 1, "Oats", 1.0, oats)))
        val state = today().uiState.first { it.goals.isNotEmpty() }
        val goal = state.goals.single()
        assertEquals(GoalType.FOOD, goal.type)
        assertEquals("Hit protein: 145 g to go", EnglishText.resolve(goal.title))
        assertEquals("35 of 180 g today", EnglishText.resolve(goal.detail))
    }

    @Test
    fun `streak counts complete weeks and the running week only once complete`() = withMain {
        fun week(monday: LocalDate) = (0L..3L).map { monday.plusDays(it) }
        // Two full weeks before this one (Mon 14 and Mon 21 Sep), 4 days each; this week has 1 so far.
        repo.completedDates.value = week(LocalDate.of(2026, 9, 14)) + week(LocalDate.of(2026, 9, 21)) + LocalDate.of(2026, 9, 28)
        assertEquals(2, today().uiState.first { !it.isLoading }.streakWeeks)
    }

    @Test
    fun `food line shows protein and the next unchecked meal`() = withMain {
        nutrition.plans.value = listOf(cut(breakfast, snack, lunch))
        nutrition.logs.value = mapOf(day to listOf(MealLogEntry(1, 21, 1, "Oats", 1.0, oats)))
        val at10 = Clock.fixed(day.atTime(10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

        val card = checkNotNull(today(at10).uiState.first { it.food != null }.food)

        assertEquals("Cut", card.planName)
        assertEquals(35.0, card.protein.eaten, 1e-9)
        assertEquals(180.0, checkNotNull(card.protein.target), 1e-9)
        // Breakfast is logged, the 09:00 snack is overdue, so lunch (13:00) is next.
        assertEquals(22L, card.nextMeal?.id)
        assertEquals(NextLine.Next("Lunch", 13 * 60), card.nextLine)
    }

    @Test
    fun `food line covers no plan and all meals done`() = withMain {
        val vm = today()
        var card = checkNotNull(vm.uiState.first { it.food != null }.food)
        assertNull(card.planName)
        assertEquals(NextLine.NoPlan, card.nextLine)

        nutrition.plans.value = listOf(cut(breakfast))
        nutrition.logs.value = mapOf(day to listOf(MealLogEntry(1, 21, 1, "Oats", 1.0, oats)))
        card = checkNotNull(vm.uiState.first { it.food?.allDone == true }.food)
        assertEquals(NextLine.AllDone, card.nextLine)
    }

    @Test
    fun `food line rolls over to the new day on refreshToday`() = withMain {
        val clock = MutableClock(day.atTime(20, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        nutrition.plans.value = listOf(cut(breakfast))
        nutrition.logs.value = mapOf(day to listOf(MealLogEntry(1, 21, 1, "Oats", 1.0, oats)))
        val vm = today(clock)
        assertEquals(NextLine.AllDone, checkNotNull(vm.uiState.first { it.food != null }.food).nextLine)

        clock.instant = day.plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC)
        vm.refreshToday()

        val card = checkNotNull(vm.uiState.first { it.food?.allDone == false }.food)
        assertEquals(NextLine.Next("Breakfast", 7 * 60 + 30), card.nextLine)
        assertEquals(0.0, card.protein.eaten, 1e-9)
    }

    @Test
    fun `the workout does not wait on nutrition and a nutrition failure only hides the food line`() = withMain {
        val never = object : NutritionRepository by nutrition {
            override fun observeDay(date: LocalDate): Flow<DayNutrition> = flow { awaitCancellation() }
        }
        var state = withTimeout(1_000) {
            today(food = never).uiState.first { !it.isLoading }
        }
        assertNotNull(state.main)
        assertNull(state.food)

        // Fails only for the first day; the chain must survive and recover on the next day.
        val failing = object : NutritionRepository by nutrition {
            override fun observeDay(date: LocalDate): Flow<DayNutrition> = if (date == day) flow { error("db down") } else nutrition.observeDay(date)
        }
        val mutable = MutableClock(day.atTime(8, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        nutrition.plans.value = listOf(cut(breakfast))
        val vm = today(mutable, failing)
        state = withTimeout(1_000) { vm.uiState.first { !it.isLoading } }
        assertNotNull(state.main)
        assertNull(state.food)

        mutable.instant = day.plusDays(1).atTime(8, 0).toInstant(ZoneOffset.UTC)
        vm.refreshToday()
        state = withTimeout(1_000) { vm.uiState.first { it.food != null } }
        assertEquals("Cut", checkNotNull(state.food).planName)
    }

    @Test
    fun `food line text covers an empty plan and an untimed next slot`() {
        val empty = TodayViewModel.buildFoodLine(
            DayNutrition(day, cut(), MacroTargets(proteinG = 180.0), emptyList(), 0, emptyList()),
            600,
            FoodTrackingMode.PLAN,
        )
        assertEquals(NextLine.NoMeals, empty.nextLine)
        assertFalse(empty.allDone)

        val untimed = PlanSlot(24, "Bedtime", 4, "Yogurt", oats, null, null)
        val card = TodayViewModel.buildFoodLine(
            DayNutrition(day, cut(untimed), MacroTargets(proteinG = 180.0), emptyList(), 0, emptyList()),
            600,
            FoodTrackingMode.PLAN,
        )
        assertEquals(NextLine.Next("Bedtime", null), card.nextLine)
    }

    @Test
    fun `on the fly food line shows kcal and protein against own targets`() {
        val entry = MealLogEntry(1, null, 1, "Oats", 2.0, oats)
        val withTarget = TodayViewModel.buildFoodLine(
            DayNutrition(day, null, MacroTargets(proteinG = 140.0), listOf(entry), 0, emptyList()),
            600,
            FoodTrackingMode.ON_THE_FLY,
        )
        assertEquals(NextLine.Logged(1042, 70, 140.0), withTarget.nextLine)
        assertNull(withTarget.nextMeal)

        val noTarget = TodayViewModel.buildFoodLine(
            DayNutrition(day, null, MacroTargets(), listOf(entry), 0, emptyList()),
            600,
            FoodTrackingMode.ON_THE_FLY,
        )
        assertEquals(NextLine.Logged(1042, 70, null), noTarget.nextLine)
    }

    @Test
    fun `on the fly food line with nothing logged is zero kcal and ignores the plan`() {
        val line = TodayViewModel.buildFoodLine(
            DayNutrition(day, cut(breakfast), MacroTargets(proteinG = 180.0), emptyList(), 0, emptyList()),
            600,
            FoodTrackingMode.ON_THE_FLY,
        )
        assertEquals(NextLine.Logged(0, 0, 180.0), line.nextLine)
        assertFalse(line.allDone)
    }

    @Test
    fun `today reports goals and other days as onboarding facts`() {
        assertEquals(emptySet<OnboardingFact>(), TodayUiState().onboardingFacts())
        val goal = GoalUi(GoalType.BEAT_LAST_TIME, uiText(R.string.common_xp_gain, 10), uiText(R.string.common_xp_gain, 10), uiText(R.string.common_xp_gain, 10))
        val otherDay = DayUi(ProgramCatalog.templates.first(), lastCompleted = null, isInProgress = false)
        assertEquals(
            setOf(OnboardingFact.GOALS_VISIBLE, OnboardingFact.OTHER_DAYS_VISIBLE),
            TodayUiState(goals = listOf(goal), otherDays = listOf(otherDay)).onboardingFacts(),
        )
        // "Do next" shows only beside a main day that isn't unfinished.
        val ready = DayUi(ProgramCatalog.templates[1], lastCompleted = null, isInProgress = false)
        assertTrue(OnboardingFact.DO_NEXT_VISIBLE in TodayUiState(main = ready, otherDays = listOf(otherDay)).onboardingFacts())
        val unfinished = ready.copy(isInProgress = true)
        assertFalse(OnboardingFact.DO_NEXT_VISIBLE in TodayUiState(main = unfinished, otherDays = listOf(otherDay)).onboardingFacts())
    }

    @Test
    fun `calendar only shows the visible month and never selects the future`() = withMain {
        repo.completedDays.value = setOf(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 31))
        val vm = LogViewModel(repo, FakeProgramRepository(), clock)

        var state = vm.uiState.first { it.completedDays.isNotEmpty() }
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(setOf(LocalDate.of(2026, 9, 1)), state.completedDays)
        assertFalse(state.canGoForward)

        vm.nextMonth() // capped at the current month
        vm.selectDay(LocalDate.of(2026, 9, 30)) // tomorrow → ignored
        state = vm.uiState.first()
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(null, state.selected)

        vm.previousMonth()
        vm.selectDay(LocalDate.of(2026, 8, 31))
        state = vm.uiState.first { it.selected != null }
        assertEquals(setOf(LocalDate.of(2026, 8, 31)), state.completedDays)
        assertEquals(LocalDate.of(2026, 8, 31), state.selected?.date)

        vm.dismissDay()
        assertEquals(null, vm.uiState.first { it.selected == null }.selected)
    }
}
