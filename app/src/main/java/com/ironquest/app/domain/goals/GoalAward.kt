package com.ironquest.app.domain.goals

import com.ironquest.app.domain.model.StatType

/** One workout goal reached at completion, with the XP it paid. */
data class GoalReached(val type: GoalType, val exerciseId: String, val exerciseName: String, val xp: Int)

/** Goal bonuses of one completion: character XP = [goalXp]; each stat gets its exercises' share. */
data class GoalAward(val goals: List<GoalReached>, val statXp: Map<StatType, Int>) {
    val goalXp: Int get() = goals.sumOf { it.xp }

    fun statXp(stat: StatType): Int = statXp[stat] ?: 0

    companion object {
        val None = GoalAward(emptyList(), emptyMap())
    }
}
