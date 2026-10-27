package com.ironquest.app.domain.model

data class ExerciseDefinition(
    val id: String,
    val name: String,
    val stat: StatType,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    /** Load jump used by the Level Up rule (2.5 kg upper body / small lifts, 5 kg big lower-body lifts). */
    val incrementKg: Double = 2.5,
)
