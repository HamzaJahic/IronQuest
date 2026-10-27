package com.ironquest.app.ui.goals

import com.ironquest.app.R
import com.ironquest.app.domain.goals.BeatLastTimeGoal
import com.ironquest.app.domain.goals.FoodGoal
import com.ironquest.app.domain.goals.FoodTarget
import com.ironquest.app.domain.goals.GoalRules
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.goals.LimitBreakGoal
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class GoalCopyTest {

    private lateinit var previous: Locale

    @Before fun pinLocale() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After fun restoreLocale() = Locale.setDefault(previous)

    private fun english(text: UiText) = EnglishText.resolve(text)

    private fun sets(weight: Double, vararg reps: Int) = reps.mapIndexed { index, repCount -> LoggedSet(index, repCount, weight) }

    private val last = mapOf(
        "bench_press" to sets(100.0, 8, 8, 8, 8),
        "barbell_row" to sets(70.0, 9, 9, 8, 7),
    )

    private val day = DayNutrition(
        date = LocalDate.of(2026, 10, 9),
        activePlan = null,
        targets = MacroTargets(proteinG = 160.0, waterMl = 2500),
        logs = listOf(MealLogEntry(1, null, 1, "Oats", 1.0, Macros(45.0, 60.0, 10.0, 5.0))),
        waterMl = 2500,
        supplements = emptyList(),
    )

    @Test
    fun `food goal titles name what is left or reached`() {
        val (protein, water) = GoalRules.forFood(day)
        assertEquals(uiText(R.string.goal_food_protein_title, uiText(R.string.unit_g_value, 115)), GoalCopy.title(protein))
        assertEquals(uiText(R.string.goal_food_water_done), GoalCopy.title(water))
        assertEquals("Hit protein: 115 g to go", english(GoalCopy.title(protein)))
        assertEquals("Water target reached", english(GoalCopy.title(water)))
    }

    @Test
    fun `labels are the three goal names`() {
        assertEquals(uiText(R.string.goal_limit_break), GoalCopy.label(GoalType.LIMIT_BREAK))
        assertEquals(uiText(R.string.goal_beat_last_time), GoalCopy.stamp(GoalType.BEAT_LAST_TIME))
        assertEquals(listOf("Limit break", "Beat last time", "Food"), GoalType.entries.map { english(GoalCopy.label(it)) })
    }

    @Test
    fun `workout goal title and detail`() {
        val list = GoalRules.forWorkout(ProgramCatalog.UPPER_1, last)
        val limit = list[0] as LimitBreakGoal
        val beat = list[1] as BeatLastTimeGoal
        assertEquals("${limit.exercise.exercise.name}: move to 102.5 kg", english(GoalCopy.title(limit)))
        assertEquals("${beat.exercise.exercise.name}: one more rep", english(GoalCopy.title(beat)))
        assertEquals("Last time: 100 kg × 8, 8, 8, 8", english(GoalCopy.detail(limit)))
        assertEquals("Last time: 70 kg × 9, 9, 8, 7", english(GoalCopy.detail(beat)))
    }

    @Test
    fun `sets read as weight and reps, reps only, or minutes`() {
        assertEquals("nothing logged", english(GoalCopy.sets(emptyList(), TrackingType.WEIGHT_REPS)))
        assertEquals("12, 10 reps", english(GoalCopy.sets(sets(0.0, 12, 10), TrackingType.WEIGHT_REPS)))
        assertEquals("70 kg × 9, 72.5 kg × 8", english(GoalCopy.sets(sets(70.0, 9) + LoggedSet(1, 8, 72.5), TrackingType.WEIGHT_REPS)))
        assertEquals("30 min", english(GoalCopy.sets(listOf(LoggedSet(0, 0, 0.0, durationMinutes = 30)), TrackingType.DURATION)))
    }

    @Test
    fun `water switches to litres at a thousand`() {
        assertEquals("750 ml", english(GoalCopy.water(750.0)))
        assertEquals("1.25 l", english(GoalCopy.water(1250.0)))
        assertEquals("2 l", english(GoalCopy.water(2000.0)))
    }

    @Test
    fun `numbers follow the locale`() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("1,25 l", english(GoalCopy.water(1250.0)))
    }

    @Test
    fun `notification text joins goals and food targets`() {
        val goals = GoalRules.forWorkout(ProgramCatalog.UPPER_1, last)
        val limit = goals[0] as LimitBreakGoal
        val beat = goals[1] as BeatLastTimeGoal
        assertEquals(
            "Limit break: ${limit.exercise.exercise.name} 102.5 kg · Beat last time: ${beat.exercise.exercise.name}",
            english(GoalCopy.workoutNotificationBody(listOf(limit, beat))),
        )
        val protein = FoodGoal(FoodTarget.PROTEIN, 45.0, 160.0)
        val water = FoodGoal(FoodTarget.WATER, 500.0, 2500.0)
        assertEquals("Food goals still open", english(GoalCopy.foodNotificationTitle(listOf(protein, water))))
        assertEquals("Water target not reached yet", english(GoalCopy.foodNotificationTitle(listOf(water))))
        assertEquals("Protein target not reached yet", english(GoalCopy.foodNotificationTitle(listOf(protein))))
        assertEquals("115 g protein to go · 2 l water to go", english(GoalCopy.foodNotificationBody(listOf(protein, water))))
        assertEquals(joinedText(emptyList(), R.string.common_dot_separator), GoalCopy.foodNotificationBody(emptyList()))
    }
}
