package com.ironquest.app.domain.repository

import com.ironquest.app.domain.onboarding.OnboardingTopic
import kotlinx.coroutines.flow.Flow

/** Which tips the user has seen, as the topic version they saw. A device preference: not in backups. */
interface OnboardingRepository {
    /** Emits the current value immediately, then on every change. */
    fun observeSeen(): Flow<Map<OnboardingTopic, Int>>

    /** Synchronous read, so a screen never flashes a tip the user already closed. */
    fun currentSeen(): Map<OnboardingTopic, Int>

    fun markSeen(topic: OnboardingTopic)

    fun markAllSeen()

    fun resetAll()
}
