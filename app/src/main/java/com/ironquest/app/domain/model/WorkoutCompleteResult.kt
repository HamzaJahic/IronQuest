package com.ironquest.app.domain.model

import com.ironquest.app.domain.goals.GoalReached

data class WorkoutCompleteResult(
    /** Completion bonus (character only). */
    val bonusXp: Int,
    /** Sets + goals + completion bonus, as stored in `workouts.xpEarned`. */
    val totalXpEarned: Int,
    val levelUp: LevelUpEvent?,
    /** Workout goals reached and paid by this completion (empty on a repeated call). */
    val goals: List<GoalReached> = emptyList(),
    /** Start of the session (row created on the first set) to completion; null if unknown. */
    val durationMillis: Long? = null,
) {
    val goalXp: Int get() = goals.sumOf { it.xp }
}
