package com.ironquest.app.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCatalogTest {

    @Test
    fun `every topic has at least one step and a positive version`() {
        OnboardingTopic.entries.forEach { topic ->
            assertTrue("$topic has no steps", topic.steps.isNotEmpty())
            assertTrue("$topic version", topic.version >= 1)
        }
    }

    @Test
    fun `every topic is reachable from some screen`() {
        OnboardingTopic.entries.forEach { topic -> assertTrue("$topic has no screen", topic.screens.isNotEmpty()) }
    }

    @Test
    fun `prerequisites have no cycles`() {
        OnboardingTopic.entries.forEach { start ->
            val visited = mutableSetOf<OnboardingTopic>()
            var current: OnboardingTopic? = start
            while (current != null) {
                assertTrue("cycle through $start", visited.add(current))
                current = current.after
            }
        }
    }

    @Test
    fun `targets are unique within a topic`() {
        OnboardingTopic.entries.forEach { topic ->
            val targets = topic.steps.mapNotNull { it.target }
            assertEquals("$topic repeats a target", targets.size, targets.toSet().size)
        }
    }

    @Test
    fun `step requirements are facts its screens can report`() {
        // A step that needs a fact the topic itself already requires is pointless.
        OnboardingTopic.entries.forEach { topic ->
            topic.steps.forEach { step -> assertTrue("$topic step ${step.target}", step.requires == null || step.requires !in topic.requires) }
        }
    }
}
