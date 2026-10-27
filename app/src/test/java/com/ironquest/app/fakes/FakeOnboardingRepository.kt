package com.ironquest.app.fakes

import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOnboardingRepository : OnboardingRepository {
    val seen = MutableStateFlow(emptyMap<OnboardingTopic, Int>())

    override fun observeSeen(): Flow<Map<OnboardingTopic, Int>> = seen

    override fun currentSeen(): Map<OnboardingTopic, Int> = seen.value

    override fun markSeen(topic: OnboardingTopic) {
        seen.value = seen.value + (topic to topic.version)
    }

    override fun markAllSeen() {
        seen.value = OnboardingTopic.entries.associateWith { it.version }
    }

    override fun resetAll() {
        seen.value = emptyMap()
    }
}
