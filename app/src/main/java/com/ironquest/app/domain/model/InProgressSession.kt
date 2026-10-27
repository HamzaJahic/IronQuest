package com.ironquest.app.domain.model

data class PersistedSet(val id: Long, val set: LoggedSet, val xpAwarded: Int)

data class InProgressSession(
    val workoutId: Long,
    val templateId: String,
    val setsByExercise: Map<String, List<PersistedSet>>,
)
