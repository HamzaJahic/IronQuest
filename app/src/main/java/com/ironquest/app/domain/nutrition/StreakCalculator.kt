package com.ironquest.app.domain.nutrition

object StreakCalculator {
    /**
     * Consecutive days (epochDay) ending today — or ending yesterday when today isn't
     * taken yet, so the streak doesn't look broken first thing in the morning.
     */
    fun currentStreak(days: Set<Long>, today: Long): Int {
        var day = if (today in days) today else today - 1
        var streak = 0
        while (day in days) {
            streak++
            day--
        }
        return streak
    }
}
