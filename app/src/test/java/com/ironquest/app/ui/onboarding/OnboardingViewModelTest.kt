package com.ironquest.app.ui.onboarding

import com.ironquest.app.domain.onboarding.OnboardingTopic
import com.ironquest.app.fakes.FakeOnboardingRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingViewModelTest {
    private val repository = FakeOnboardingRepository()
    private val viewModel = OnboardingViewModel(repository)

    @Test
    fun `finish records the topic's version`() {
        viewModel.finish(OnboardingTopic.LOG)
        assertEquals(mapOf(OnboardingTopic.LOG to OnboardingTopic.LOG.version), repository.seen.value)
    }

    @Test
    fun `skip all marks every topic`() {
        viewModel.skipAll()
        assertEquals(OnboardingTopic.entries.toSet(), repository.seen.value.keys)
    }

    @Test
    fun `the first value is read synchronously`() {
        repository.markSeen(OnboardingTopic.WELCOME)
        assertEquals(setOf(OnboardingTopic.WELCOME), OnboardingViewModel(repository).seen.value.keys)
    }
}
