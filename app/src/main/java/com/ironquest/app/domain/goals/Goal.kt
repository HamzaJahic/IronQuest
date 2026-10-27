package com.ironquest.app.domain.goals

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.progression.XpRules

sealed interface Goal {
    val type: GoalType

    /** XP paid when the goal is reached; food goals pay the existing nutrition bonuses instead. */
    val bonusXp: Int
}

/** A goal about one exercise of today's workout, judged from this session's sets at completion. */
sealed interface WorkoutGoal : Goal {
    val exercise: TemplateExercise

    /** The last completed session's sets of [exercise], ordered by index. */
    val lastSets: List<LoggedSet>
}

/** Last time every prescribed set hit the top of the range at the working weight: move up. */
data class LimitBreakGoal(
    override val exercise: TemplateExercise,
    override val lastSets: List<LoggedSet>,
    val previousWeightKg: Double,
    val targetWeightKg: Double,
) : WorkoutGoal {
    override val type get() = GoalType.LIMIT_BREAK
    override val bonusXp get() = XpRules.LIMIT_BREAK_BONUS
}

/** Any exercise with history and no Limit break goal: more volume than last time. */
data class BeatLastTimeGoal(
    override val exercise: TemplateExercise,
    override val lastSets: List<LoggedSet>,
    val lastVolume: Double,
) : WorkoutGoal {
    override val type get() = GoalType.BEAT_LAST_TIME
    override val bonusXp get() = XpRules.BEAT_LAST_TIME_BONUS
}
