package com.ironquest.app.domain.model

import java.time.LocalDate

/** One exercise of a past-workout form: its sets in order (set indexes are assigned on save). */
data class DraftExercise(
    val exercise: ExerciseDefinition,
    val repRange: RepRange,
    val sets: List<LoggedSet>,
)

/**
 * A finished session written after the fact. [workoutId] null creates one on [date]; otherwise
 * the stored session is rewritten (date and times stay). Exercises without sets are ignored.
 */
data class WorkoutLogDraft(
    val workoutId: Long?,
    val templateId: String,
    val date: LocalDate,
    val exercises: List<DraftExercise>,
)

data class SaveWorkoutResult(
    val workoutId: Long,
    /** Net change of the character's XP (negative when an edit removed sets). */
    val xpDelta: Int,
    val levelUp: LevelUpEvent?,
)
