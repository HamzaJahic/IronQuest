package com.ironquest.app.domain.model

import com.ironquest.app.domain.progression.SetXp

data class LogSetRequest(
    val templateId: String,
    /** null → first set of the session; the repository creates the workout row. */
    val workoutId: Long?,
    val exercise: TemplateExercise,
    val set: LoggedSet,
    val ghost: LoggedSet?,
)

data class LogSetResult(
    val setId: Long,
    val workoutId: Long,
    val xp: SetXp,
    /** Non-null when this set crossed a character or stat level threshold. */
    val levelUp: LevelUpEvent?,
)
