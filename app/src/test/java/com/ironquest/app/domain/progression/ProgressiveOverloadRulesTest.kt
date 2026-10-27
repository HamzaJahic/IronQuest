package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.program.ProgramCatalog.Exercises
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveOverloadRulesTest {

    private val bench = TemplateExercise(Exercises.BENCH_PRESS, targetSets = 4, repRange = RepRange(5, 8))
    private val squat = TemplateExercise(Exercises.BACK_SQUAT, targetSets = 4, repRange = RepRange(5, 8))

    private fun sets(weight: Double, vararg reps: Int) = reps.mapIndexed { index, repCount -> LoggedSet(setIndex = index, reps = repCount, weightKg = weight) }

    @Test
    fun `no history is a first encounter`() {
        assertEquals(OverloadSuggestion.FirstEncounter, ProgressiveOverloadRules.suggest(bench, emptyList()))
    }

    @Test
    fun `cardio is not weight-progressed`() {
        val cycling = TemplateExercise(Exercises.CYCLING, 1, RepRange(10, 15))
        val previous = listOf(LoggedSet(0, reps = 0, weightKg = 0.0, durationMinutes = 15))
        assertEquals(OverloadSuggestion.NotApplicable, ProgressiveOverloadRules.suggest(cycling, previous))
    }

    @Test
    fun `rule 1 - all sets at top of range levels up by 2_5kg on upper body`() {
        val result = ProgressiveOverloadRules.suggest(bench, sets(100.0, 8, 8, 8, 8))
        assertEquals(OverloadSuggestion.LevelUp(previousWeightKg = 100.0, incrementKg = 2.5), result)
        assertEquals(102.5, checkNotNull(result.suggestedWeightKg), 0.0)
    }

    @Test
    fun `rule 1 - big lower body lift levels up by 5kg`() {
        val result = ProgressiveOverloadRules.suggest(squat, sets(120.0, 8, 9, 8, 8))
        assertEquals(125.0, checkNotNull(result.suggestedWeightKg), 0.0)
    }

    @Test
    fun `rule 1 needs the full prescription - three perfect sets out of four is a hold`() {
        assertTrue(ProgressiveOverloadRules.suggest(bench, sets(100.0, 8, 8, 8)) is OverloadSuggestion.Hold)
    }

    @Test
    fun `rule 1 needs every set at working weight - a lighter back-off set blocks level up`() {
        val previous = sets(100.0, 8, 8, 8) + LoggedSet(3, reps = 8, weightKg = 90.0)
        val result = ProgressiveOverloadRules.suggest(bench, previous)
        assertTrue(result is OverloadSuggestion.Hold)
        assertEquals(100.0, checkNotNull(result.suggestedWeightKg), 0.0)
    }

    @Test
    fun `rule 2 - mid range holds the weight`() {
        val result = ProgressiveOverloadRules.suggest(bench, sets(100.0, 8, 7, 6, 6))
        assertEquals(OverloadSuggestion.Hold(weightKg = 100.0, setsAtTop = 1, totalSets = 4), result)
    }

    @Test
    fun `rule 2 - a single missed set is still a hold, not a deload`() {
        assertTrue(ProgressiveOverloadRules.suggest(bench, sets(100.0, 7, 6, 5, 4)) is OverloadSuggestion.Hold)
    }

    @Test
    fun `rule 3 - two or more failed sets deloads by 10 percent`() {
        val result = ProgressiveOverloadRules.suggest(bench, sets(100.0, 6, 5, 4, 3))
        assertEquals(
            OverloadSuggestion.Deload(previousWeightKg = 100.0, suggestedWeightKg = 90.0, failedSets = 2),
            result,
        )
    }

    @Test
    fun `rule 3 - deload rounds down to the nearest 2_5kg plate step`() {
        // 62.5 * 0.9 = 56.25 -> 55.0
        assertEquals(55.0, checkNotNull(ProgressiveOverloadRules.suggest(bench, sets(62.5, 4, 4, 4, 4)).suggestedWeightKg), 0.0)
        // 105 * 0.9 = 94.5 -> 92.5
        assertEquals(92.5, checkNotNull(ProgressiveOverloadRules.suggest(bench, sets(105.0, 3, 3, 5, 5)).suggestedWeightKg), 0.0)
    }

    @Test
    fun `bodyweight exercise with zero load never deloads below zero`() {
        assertTrue(ProgressiveOverloadRules.suggest(bench, sets(0.0, 2, 2, 2, 2)) is OverloadSuggestion.Hold)
    }

    @Test
    fun `roundDownToStep absorbs floating point noise`() {
        assertEquals(90.0, ProgressiveOverloadRules.roundDownToStep(100.0 * 0.9, 2.5), 0.0)
        assertEquals(0.0, ProgressiveOverloadRules.roundDownToStep(2.25, 2.5), 0.0)
    }
}
