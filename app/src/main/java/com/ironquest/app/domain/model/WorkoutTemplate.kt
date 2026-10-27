package com.ironquest.app.domain.model

data class WorkoutTemplate(
    val id: String,
    val name: String,
    val exercises: List<TemplateExercise>,
) {
    /** The first exercise is the "boss" lift the workout is named after. */
    val mainLift: TemplateExercise get() = exercises.first()

    /** Pages of the workout screen: single exercises and supersets, in order. */
    val blocks: List<WorkoutBlock> get() = WorkoutBlock.of(exercises)
}
