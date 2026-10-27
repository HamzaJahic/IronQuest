package com.ironquest.app.domain.repository

/** Erases what the user logged, never what they set up. */
interface DataResetRepository {
    /**
     * Deletes every workout and set, every logged meal, water and supplement intake, and resets
     * level, XP, stats and workout count. Programs, exercises, meals, meal plans, supplements and
     * settings stay.
     */
    suspend fun wipeLoggedData()
}
