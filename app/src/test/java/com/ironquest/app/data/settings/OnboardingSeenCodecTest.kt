package com.ironquest.app.data.settings

import com.ironquest.app.domain.onboarding.OnboardingTopic
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingSeenCodecTest {
    @Test
    fun `reads known topics and ignores the rest`() {
        val stored = mapOf("seen_welcome" to 1, "seen_gone_topic" to 3, "seen_today" to "oops", "other" to true)
        assertEquals(mapOf(OnboardingTopic.WELCOME to 1), OnboardingSeenCodec.decode(stored))
    }

    @Test
    fun `key round-trips`() = OnboardingTopic.entries.forEach { topic ->
        assertEquals(mapOf(topic to 2), OnboardingSeenCodec.decode(mapOf(OnboardingSeenCodec.key(topic) to 2)))
    }
}
