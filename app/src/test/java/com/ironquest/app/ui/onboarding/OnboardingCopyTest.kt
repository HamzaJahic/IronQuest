package com.ironquest.app.ui.onboarding

import com.ironquest.app.domain.onboarding.OnboardingTopic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OnboardingCopyTest {

    @Test
    fun `every step of every topic has a title and a body`() {
        OnboardingTopic.entries.forEach { topic ->
            assertEquals("$topic copy count", topic.steps.size, OnboardingCopy.steps(topic).size)
            topic.steps.indices.forEach { index ->
                assertNotEquals("$topic $index title", 0, OnboardingCopy.title(topic, index))
                assertNotEquals("$topic $index body", 0, OnboardingCopy.body(topic, index))
            }
        }
    }

    @Test
    fun `no string is used twice`() {
        val ids = OnboardingTopic.entries.flatMap { topic -> OnboardingCopy.steps(topic).flatMap { (title, body) -> listOf(title, body) } }
        assertEquals(ids.size, ids.toSet().size)
    }
}
