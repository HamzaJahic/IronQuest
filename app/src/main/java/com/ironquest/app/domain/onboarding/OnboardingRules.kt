package com.ironquest.app.domain.onboarding

/** Which tip to show, and which of its steps. Pure, so every timing rule has a unit test. */
object OnboardingRules {

    fun isSeen(topic: OnboardingTopic, seen: Map<OnboardingTopic, Int>): Boolean = (seen[topic] ?: 0) >= topic.version

    /** Drops steps whose fact is missing; a skipped step keeps its index in [OnboardingTopic.steps]. */
    fun stepsFor(topic: OnboardingTopic, facts: Set<OnboardingFact>): List<OnboardingStep> = topic.steps.filter { step -> step.requires == null || step.requires in facts }

    /**
     * [factsAtVisitStart] and [shownThisVisit] are the gate's per-visit memory: after one tip, only a
     * contextual tip whose situation came up during this visit may follow, so tips never chain on arrival.
     */
    fun next(
        screen: OnboardingScreen,
        seen: Map<OnboardingTopic, Int>,
        facts: Set<OnboardingFact>,
        factsAtVisitStart: Set<OnboardingFact>,
        shownThisVisit: Set<OnboardingTopic>,
    ): OnboardingTopic? {
        if (OnboardingFact.BLOCKED in facts) return null
        return OnboardingTopic.entries.firstOrNull { topic ->
            val after = topic.after
            screen in topic.screens &&
                !isSeen(topic, seen) &&
                topic !in shownThisVisit &&
                (after == null || isSeen(after, seen)) &&
                facts.containsAll(topic.requires) &&
                stepsFor(topic, facts).isNotEmpty() &&
                (shownThisVisit.isEmpty() || arrivedDuringVisit(topic, factsAtVisitStart))
        }
    }

    private fun arrivedDuringVisit(topic: OnboardingTopic, factsAtVisitStart: Set<OnboardingFact>): Boolean = topic.requires.isNotEmpty() && !factsAtVisitStart.containsAll(topic.requires)
}
