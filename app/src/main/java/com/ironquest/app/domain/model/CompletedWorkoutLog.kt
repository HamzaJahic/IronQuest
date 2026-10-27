package com.ironquest.app.domain.model

import java.time.LocalDate

data class ExerciseLog(
    val exerciseId: String,
    val name: String,
    val stat: StatType,
    val trackingType: TrackingType,
    val sets: List<LoggedSet>,
    /** Prescription snapshotted when the sets were logged. */
    val repRange: RepRange,
)

data class CompletedWorkoutLog(
    val workoutId: Long,
    val templateId: String,
    /** Start date of the session (what the calendar keys on). */
    val date: LocalDate,
    /** Null = the day belonged to an imported program that was deleted since. */
    val templateName: String?,
    val completedAtMillis: Long,
    val xpEarned: Int,
    val exercises: List<ExerciseLog>,
)
