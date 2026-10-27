package com.ironquest.app.domain.model

data class TemplateExercise(
    val exercise: ExerciseDefinition,
    val targetSets: Int,
    val repRange: RepRange,
    /**
     * Superset key, unique within the day; consecutive exercises with the same key are one
     * superset (see WorkoutBlocks). null = standalone. The built-in catalog has none.
     */
    val supersetGroup: String? = null,
)
