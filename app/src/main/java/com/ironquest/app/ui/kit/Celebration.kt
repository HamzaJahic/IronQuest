package com.ironquest.app.ui.kit

import com.ironquest.app.R
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.model.StatLevelUp
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiText
import com.ironquest.app.ui.you.nameRes

/** Stamp text for a level-up: "Level 8", or the stat when only a stat moved ("Strength 5"). */
fun LevelUpEvent.stampText(): UiText = if (characterLeveledUp) {
    uiText(R.string.celebration_level, newLevel)
} else {
    statLevelUps.firstOrNull()?.statLevel() ?: uiText(R.string.celebration_level, newLevel)
}

/** The one-line entry a stamp settles into: "Level 8 reached", "Level 8 reached · Strength 5", "Strength 5 reached". */
fun LevelUpEvent.reachedLine(): UiText {
    val stats = joinedText(statLevelUps.map { it.statLevel() }, R.string.common_dot_separator)
    return when {
        characterLeveledUp && statLevelUps.isNotEmpty() -> uiText(R.string.celebration_level_stats_reached, newLevel, stats)
        characterLeveledUp -> uiText(R.string.celebration_level_reached, newLevel)
        else -> uiText(R.string.celebration_stats_reached, stats)
    }
}

private fun StatLevelUp.statLevel(): UiText = uiText(R.string.celebration_stat_level, uiText(stat.nameRes()), newLevel)
