package com.ironquest.app.domain.model

import java.time.LocalDate

/** "Ghost" = last completed session of the same template, keyed by exercise id, sets ordered by index. */
data class GhostSession(
    val workoutId: Long,
    val date: LocalDate,
    val setsByExercise: Map<String, List<LoggedSet>>,
) {
    fun ghostFor(exerciseId: String, setIndex: Int): LoggedSet? = setsByExercise[exerciseId]?.firstOrNull { it.setIndex == setIndex }
}
