package com.ironquest.app.domain.model

import com.ironquest.app.domain.progression.LevelProgress

data class PlayerProfile(
    val totalXp: Long,
    val level: LevelProgress,
    val stats: List<StatProgress>,
    val questsCompleted: Int,
)

data class StatProgress(val stat: StatType, val xp: Long, val progress: LevelProgress)
