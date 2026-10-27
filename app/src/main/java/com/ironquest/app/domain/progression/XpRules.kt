package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TrackingType

data class SetXp(val base: Int, val ghostSlayerBonus: Int) {
    val total: Int get() = base + ghostSlayerBonus
}

/** Every XP number in the game lives here so balancing is a one-file change. */
object XpRules {
    const val SET_XP = 10
    const val GHOST_SLAYER_BONUS = 5
    const val QUEST_COMPLETE_BONUS = 100

    /** Goal bonuses, paid once at workout completion (character + the exercise's stat). */
    const val LIMIT_BREAK_BONUS = 25
    const val BEAT_LAST_TIME_BONUS = 10

    fun forSet(set: LoggedSet, ghost: LoggedSet?, trackingType: TrackingType): SetXp {
        val base = when (trackingType) {
            TrackingType.WEIGHT_REPS -> SET_XP
            // 1 XP per minute of cardio, never less than a normal set.
            TrackingType.DURATION -> maxOf(SET_XP, set.durationMinutes ?: 0)
        }
        val bonus = if (ghost != null && beatsGhost(set, ghost, trackingType)) GHOST_SLAYER_BONUS else 0
        return SetXp(base, bonus)
    }

    /**
     * A set "slays" its ghost when it dominates last session's set at the same index:
     * at least as heavy with more reps, or heavier with at least as many reps.
     * (Pure volume would reward swapping to a lighter weight for junk reps.)
     */
    fun beatsGhost(set: LoggedSet, ghost: LoggedSet, trackingType: TrackingType): Boolean = when (trackingType) {
        TrackingType.WEIGHT_REPS ->
            (set.weightKg >= ghost.weightKg && set.reps > ghost.reps) ||
                (set.weightKg > ghost.weightKg && set.reps >= ghost.reps)
        TrackingType.DURATION -> (set.durationMinutes ?: 0) > (ghost.durationMinutes ?: 0)
    }
}
