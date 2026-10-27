package com.ironquest.app.domain.model

/** One performed set, independent of storage. */
data class LoggedSet(
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val durationMinutes: Int? = null,
)
