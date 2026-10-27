package com.ironquest.app.domain.goals

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.progression.XpRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {

    private val all = GoalType.entries.toSet()

    private fun sets(weight: Double, vararg reps: Int) = reps.mapIndexed { index, repCount -> LoggedSet(index, repCount, weight) }

    // Upper 1: bench 4 × 5–8, row 4 × 6–10, curl 2 × 10–15.
    private val last = mapOf(
        "bench_press" to sets(100.0, 8, 8, 8, 8), // top of the range → Limit break to 102.5
        "barbell_row" to sets(70.0, 9, 9, 8, 7), // inside → Beat last time, volume 2310
    )

    @Test
    fun `goal bonuses are 25 and 10`() {
        assertEquals(25, XpRules.LIMIT_BREAK_BONUS)
        assertEquals(10, XpRules.BEAT_LAST_TIME_BONUS)
    }

    @Test
    fun `volume is weight times reps, reps for bodyweight, minutes for cardio`() {
        assertEquals(2310.0, GoalRules.volume(sets(70.0, 9, 9, 8, 7), TrackingType.WEIGHT_REPS), 1e-9)
        assertEquals(22.0, GoalRules.volume(sets(0.0, 12, 10), TrackingType.WEIGHT_REPS), 1e-9)
        // Mixed: 60 × 10 + bodyweight 5.
        assertEquals(605.0, GoalRules.volume(listOf(LoggedSet(0, 10, 60.0), LoggedSet(1, 5, 0.0)), TrackingType.WEIGHT_REPS), 1e-9)
        val ride = listOf(LoggedSet(0, reps = 0, weightKg = 0.0, durationMinutes = 25))
        assertEquals(25.0, GoalRules.volume(ride, TrackingType.DURATION), 1e-9)
        assertEquals(0.0, GoalRules.volume(emptyList(), TrackingType.WEIGHT_REPS), 1e-9)
    }

    @Test
    fun `workout goals - limit break after a level up, beat last time for other history`() {
        val list = GoalRules.forWorkout(ProgramCatalog.UPPER_1, last)
        assertEquals(2, list.size)
        val limit = list[0] as LimitBreakGoal
        assertEquals("bench_press", limit.exercise.exercise.id)
        assertEquals(102.5, limit.targetWeightKg, 1e-9)
        val beat = list[1] as BeatLastTimeGoal
        assertEquals("barbell_row", beat.exercise.exercise.id)
        assertEquals(2310.0, beat.lastVolume, 1e-9)
        // No history → no goal.
        assertTrue(GoalRules.forWorkout(ProgramCatalog.UPPER_1, emptyMap()).isEmpty())
    }

    @Test
    fun `limit break needs every prescribed set at the new weight, reps don't matter`() {
        val limit = GoalRules.forWorkout(ProgramCatalog.UPPER_1, last)[0]
        assertTrue(GoalRules.isDone(limit, sets(102.5, 5, 5, 5, 5)))
        assertFalse(GoalRules.isDone(limit, sets(102.5, 8, 8, 8))) // only 3 of 4
        assertFalse(GoalRules.isDone(limit, sets(102.5, 8, 8, 8) + LoggedSet(3, 8, 100.0)))
        assertTrue(GoalRules.isDone(limit, sets(105.0, 5, 5, 5, 5)))
    }

    @Test
    fun `beat last time needs strictly more volume`() {
        val beat = GoalRules.forWorkout(ProgramCatalog.UPPER_1, last)[1]
        assertFalse(GoalRules.isDone(beat, sets(70.0, 9, 9, 8, 7))) // equal
        assertTrue(GoalRules.isDone(beat, sets(70.0, 9, 9, 8, 8))) // one more rep
        assertTrue(GoalRules.isDone(beat, sets(80.0, 8, 8, 8, 6))) // 2400
    }

    @Test
    fun `completion pays reached goals of enabled types to character and stat`() {
        val session = mapOf(
            "bench_press" to sets(102.5, 6, 6, 5, 5),
            "barbell_row" to sets(70.0, 10, 9, 8, 7),
        )
        val award = GoalRules.completionAward(ProgramCatalog.UPPER_1, last, session, all)
        assertEquals(listOf(GoalType.LIMIT_BREAK, GoalType.BEAT_LAST_TIME), award.goals.map { it.type })
        assertEquals(35, award.goalXp)
        assertEquals(35, award.statXp(StatType.STRENGTH))
        assertEquals(0, award.statXp(StatType.CONSTITUTION))

        val onlyBeat = GoalRules.completionAward(ProgramCatalog.UPPER_1, last, session, setOf(GoalType.BEAT_LAST_TIME))
        assertEquals(10, onlyBeat.goalXp)
        assertEquals(0, GoalRules.completionAward(ProgramCatalog.UPPER_1, last, session, emptySet()).goalXp)
    }

    @Test
    fun `completion pays nothing for missed goals and goals feed the right stat`() {
        val missed = GoalRules.completionAward(ProgramCatalog.UPPER_1, last, mapOf("bench_press" to sets(100.0, 8, 8, 8, 8)), all)
        assertEquals(0, missed.goalXp)

        // Lower 1: squat (CON) and the cycling finisher (STA, minutes).
        val lowerLast = mapOf(
            "back_squat" to sets(100.0, 6, 6, 6, 6),
            "cycling" to listOf(LoggedSet(0, 0, 0.0, durationMinutes = 15)),
        )
        val lowerNow = mapOf(
            "back_squat" to sets(100.0, 7, 6, 6, 6),
            "cycling" to listOf(LoggedSet(0, 0, 0.0, durationMinutes = 16)),
        )
        val award = GoalRules.completionAward(ProgramCatalog.LOWER_1, lowerLast, lowerNow, all)
        assertEquals(10, award.statXp(StatType.CONSTITUTION))
        assertEquals(10, award.statXp(StatType.STAMINA))
        assertEquals(20, award.goalXp)
    }

    @Test
    fun `food goals come from the plan targets`() {
        val day = DayNutrition(
            date = LocalDate.of(2026, 10, 9),
            activePlan = null,
            targets = MacroTargets(proteinG = 160.0, waterMl = 2500),
            logs = listOf(MealLogEntry(1, null, 1, "Oats", 1.0, Macros(45.0, 60.0, 10.0, 5.0))),
            waterMl = 2500,
            supplements = emptyList(),
        )
        val (protein, water) = GoalRules.forFood(day)
        assertEquals(FoodTarget.PROTEIN, protein.target)
        assertEquals(115.0, protein.remaining, 1e-9)
        assertFalse(protein.isDone)
        assertTrue(water.isDone)
        assertTrue(GoalRules.forFood(day.copy(targets = null)).isEmpty())
    }
}
