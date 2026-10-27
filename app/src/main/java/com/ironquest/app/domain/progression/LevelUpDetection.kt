package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.model.StatLevelUp
import com.ironquest.app.domain.model.StatType

/** Raw XP counters, as stored on the character sheet. */
data class XpSnapshot(
    val totalXp: Long,
    val statXp: Map<StatType, Long>,
)

/** Compares XP before/after an award; returns null when no threshold was crossed. */
fun detectLevelUp(before: XpSnapshot, after: XpSnapshot): LevelUpEvent? {
    val previousLevel = LevelCurve.CHARACTER.progressFor(before.totalXp).level
    val newLevel = LevelCurve.CHARACTER.progressFor(after.totalXp).level
    val statLevelUps = StatType.entries.mapNotNull { stat ->
        val old = LevelCurve.STAT.progressFor(before.statXp[stat] ?: 0).level
        val new = LevelCurve.STAT.progressFor(after.statXp[stat] ?: 0).level
        if (new > old) StatLevelUp(stat, new) else null
    }
    return if (newLevel > previousLevel || statLevelUps.isNotEmpty()) {
        LevelUpEvent(previousLevel, newLevel, statLevelUps)
    } else {
        null
    }
}

/**
 * Character XP can go down (un-logging a meal), so the same level can be crossed again.
 * Only a character level above [highestLevelReached] is reported; stat level-ups (stats never
 * lose XP) pass through unchanged. Null when nothing is left to report.
 */
fun gateLevelUp(event: LevelUpEvent?, highestLevelReached: Int): LevelUpEvent? {
    if (event == null) return null
    if (event.newLevel > highestLevelReached) return event
    if (event.statLevelUps.isEmpty()) return null
    return LevelUpEvent(previousLevel = event.newLevel, newLevel = event.newLevel, statLevelUps = event.statLevelUps)
}

/** The highest character level after an award: never lowered by losing XP. */
fun raisedHighestLevel(highestLevelReached: Int, totalXpAfter: Long): Int = maxOf(highestLevelReached, LevelCurve.CHARACTER.progressFor(totalXpAfter).level)
