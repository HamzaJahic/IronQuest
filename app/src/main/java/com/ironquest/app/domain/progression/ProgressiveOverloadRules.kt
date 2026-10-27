package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.TrackingType
import kotlin.math.floor

/** What the engine recommends for today's session of one exercise. */
sealed interface OverloadSuggestion {
    /** Weight to prefill into today's inputs when the user accepts the suggestion. */
    val suggestedWeightKg: Double?

    /** No history for this exercise in this workout yet. */
    data object FirstEncounter : OverloadSuggestion {
        override val suggestedWeightKg: Double? = null
    }

    /** Cardio / non-load exercises: nothing to progress by weight. */
    data object NotApplicable : OverloadSuggestion {
        override val suggestedWeightKg: Double? = null
    }

    /** Rule 1: every set reached the top of the rep range → add load. */
    data class LevelUp(
        val previousWeightKg: Double,
        val incrementKg: Double,
    ) : OverloadSuggestion {
        override val suggestedWeightKg: Double = previousWeightKg + incrementKg
    }

    /** Rule 2: performance landed inside the range → keep the load, chase reps. */
    data class Hold(
        val weightKg: Double,
        val setsAtTop: Int,
        val totalSets: Int,
    ) : OverloadSuggestion {
        override val suggestedWeightKg: Double = weightKg
    }

    /** Rule 3: multiple sets fell below the range → drop ~10% and rebuild form. */
    data class Deload(
        val previousWeightKg: Double,
        override val suggestedWeightKg: Double,
        val failedSets: Int,
    ) : OverloadSuggestion
}

/**
 * Progressive Overload Engine.
 *
 * Evaluates the sets from the last completed session of the same workout and returns
 * one of three rules:
 *
 * 1. **Level Up** – the user logged at least the prescribed number of sets, every set was
 *    performed at the working (heaviest) weight, and every set reached the top of the rep
 *    range → suggest `working weight + exercise increment` (2.5 kg or 5 kg).
 * 2. **Deload** – [DELOAD_FAILED_SET_THRESHOLD] or more sets fell below the bottom of the
 *    rep range → suggest `working weight × 0.9`, rounded *down* to the nearest [PLATE_STEP_KG].
 * 3. **Hold** – anything in between (including a single missed set) → same weight.
 *
 * Deliberately pure and synchronous so it is trivially unit-testable; the ViewModel
 * feeds it data it already loaded from Room.
 */
object ProgressiveOverloadRules {

    fun suggest(
        exercise: TemplateExercise,
        previousSets: List<LoggedSet>,
    ): OverloadSuggestion {
        if (exercise.exercise.trackingType != TrackingType.WEIGHT_REPS) return OverloadSuggestion.NotApplicable
        if (previousSets.isEmpty()) return OverloadSuggestion.FirstEncounter

        val range = exercise.repRange
        val workingWeight = previousSets.maxOf { it.weightKg }

        val completedPrescription = previousSets.size >= exercise.targetSets
        val allSetsAtTop = previousSets.all { it.weightKg >= workingWeight && it.reps >= range.max }
        if (completedPrescription && allSetsAtTop) {
            return OverloadSuggestion.LevelUp(
                previousWeightKg = workingWeight,
                incrementKg = exercise.exercise.incrementKg,
            )
        }

        val failedSets = previousSets.count { it.reps < range.min }
        if (failedSets >= DELOAD_FAILED_SET_THRESHOLD && workingWeight > 0.0) {
            return OverloadSuggestion.Deload(
                previousWeightKg = workingWeight,
                suggestedWeightKg = roundDownToStep(workingWeight * DELOAD_FACTOR, PLATE_STEP_KG),
                failedSets = failedSets,
            )
        }

        return OverloadSuggestion.Hold(
            weightKg = workingWeight,
            setsAtTop = previousSets.count { it.reps >= range.max },
            totalSets = previousSets.size,
        )
    }

    const val DELOAD_FAILED_SET_THRESHOLD = 2
    const val DELOAD_FACTOR = 0.9
    const val PLATE_STEP_KG = 2.5

    /** Rounds down so a deload never ends up heavier than 90%. Epsilon absorbs FP noise (e.g. 90.0000001). */
    fun roundDownToStep(value: Double, step: Double): Double = (floor(value / step + 1e-9) * step).coerceAtLeast(0.0)
}

/** 100.0 → "100", 102.5 → "102.5", 56.25 → "56.25" */
fun Double.formatKg(): String = if (this % 1.0 == 0.0) toLong().toString() else toBigDecimal().stripTrailingZeros().toPlainString()
