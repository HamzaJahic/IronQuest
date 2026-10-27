package com.ironquest.app.domain.progression

/**
 * Level curve where going from level `L` to `L + 1` costs `base + (L - 1) * step` XP.
 * Character: 100, 150, 200, …  Stats: 40, 60, 80, …
 */
data class LevelCurve(val base: Int, val step: Int) {

    fun xpToAdvanceFrom(level: Int): Int = base + (level - 1) * step

    /** Total XP needed to *reach* [level] from zero. */
    fun totalXpForLevel(level: Int): Long {
        val completedLevels = (level - 1).toLong()
        return base * completedLevels + step * completedLevels * (completedLevels - 1) / 2
    }

    fun progressFor(totalXp: Long): LevelProgress {
        var level = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (level < MAX_LEVEL && remaining >= xpToAdvanceFrom(level)) {
            remaining -= xpToAdvanceFrom(level)
            level++
        }
        return LevelProgress(
            level = level,
            xpIntoLevel = remaining.toInt(),
            xpForNextLevel = xpToAdvanceFrom(level),
        )
    }

    companion object {
        const val MAX_LEVEL = 999
        val CHARACTER = LevelCurve(base = 100, step = 50)
        val STAT = LevelCurve(base = 40, step = 20)
    }
}

data class LevelProgress(
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
) {
    val fraction: Float get() = if (xpForNextLevel == 0) 0f else xpIntoLevel.toFloat() / xpForNextLevel
}
