package com.ironquest.app.domain.model

data class StatLevelUp(val stat: StatType, val newLevel: Int)

data class LevelUpEvent(
    val previousLevel: Int,
    val newLevel: Int,
    val statLevelUps: List<StatLevelUp>,
) {
    val characterLeveledUp: Boolean get() = newLevel > previousLevel
}
