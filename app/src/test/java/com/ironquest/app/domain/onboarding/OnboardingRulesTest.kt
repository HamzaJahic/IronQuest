package com.ironquest.app.domain.onboarding

import com.ironquest.app.domain.onboarding.OnboardingFact.BLOCKED
import com.ironquest.app.domain.onboarding.OnboardingFact.GOALS_VISIBLE
import com.ironquest.app.domain.onboarding.OnboardingFact.SET_LOGGED
import com.ironquest.app.domain.onboarding.OnboardingFact.SUPERSET_VISIBLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingRulesTest {

    private fun seen(vararg topics: OnboardingTopic) = topics.associateWith { it.version }

    private fun next(
        screen: OnboardingScreen,
        seen: Map<OnboardingTopic, Int> = emptyMap(),
        facts: Set<OnboardingFact> = emptySet(),
        factsAtVisitStart: Set<OnboardingFact> = facts,
        shownThisVisit: Set<OnboardingTopic> = emptySet(),
    ) = OnboardingRules.next(screen, seen, facts, factsAtVisitStart, shownThisVisit)

    @Test
    fun `welcome comes first on Today`() = assertEquals(OnboardingTopic.WELCOME, next(OnboardingScreen.TODAY))

    @Test
    fun `today waits for welcome, then shows`() = assertEquals(OnboardingTopic.TODAY, next(OnboardingScreen.TODAY, seen(OnboardingTopic.WELCOME)))

    @Test
    fun `a blocked screen shows nothing`() = assertNull(next(OnboardingScreen.TODAY, facts = setOf(BLOCKED)))

    @Test
    fun `seen topics stay hidden`() = assertNull(next(OnboardingScreen.LOG_EDITOR, seen(OnboardingTopic.LOG_EDITOR)))

    @Test
    fun `an older seen version shows the tip again`() = assertEquals(OnboardingTopic.LOG_EDITOR, next(OnboardingScreen.LOG_EDITOR, mapOf(OnboardingTopic.LOG_EDITOR to 0)))

    @Test
    fun `a contextual topic waits for its fact`() {
        val workoutSeen = seen(OnboardingTopic.WORKOUT)
        assertNull(next(OnboardingScreen.WORKOUT, workoutSeen))
        assertEquals(OnboardingTopic.WORKOUT_LOGGED_SET, next(OnboardingScreen.WORKOUT, workoutSeen, setOf(SET_LOGGED)))
    }

    @Test
    fun `one intro per visit`() = assertNull(
        next(OnboardingScreen.TODAY, seen(OnboardingTopic.WELCOME), shownThisVisit = setOf(OnboardingTopic.WELCOME)),
    )

    @Test
    fun `a contextual topic may follow in the same visit when its fact arrived during the visit`() = assertEquals(
        OnboardingTopic.WORKOUT_LOGGED_SET,
        next(
            OnboardingScreen.WORKOUT,
            seen(OnboardingTopic.WORKOUT),
            facts = setOf(SET_LOGGED),
            factsAtVisitStart = emptySet(),
            shownThisVisit = setOf(OnboardingTopic.WORKOUT),
        ),
    )

    @Test
    fun `a contextual topic whose fact was already there waits for the next visit`() = assertNull(
        next(
            OnboardingScreen.TODAY,
            seen(OnboardingTopic.WELCOME, OnboardingTopic.TODAY),
            facts = setOf(GOALS_VISIBLE),
            shownThisVisit = setOf(OnboardingTopic.TODAY),
        ),
    )

    @Test
    fun `priority follows declaration order`() = assertEquals(
        OnboardingTopic.WORKOUT_LOGGED_SET,
        next(OnboardingScreen.WORKOUT, seen(OnboardingTopic.WORKOUT), setOf(SET_LOGGED, SUPERSET_VISIBLE)),
    )

    @Test
    fun `a topic shows on any of its screens`() = assertEquals(OnboardingTopic.FOOD_OVERVIEW, next(OnboardingScreen.FOOD_MONTH))

    @Test
    fun `steps without their fact are dropped`() {
        assertEquals(3, OnboardingRules.stepsFor(OnboardingTopic.TODAY, emptySet()).size)
        // An unfinished workout hides "Do next", so its step waits for its own fact.
        assertEquals(4, OnboardingRules.stepsFor(OnboardingTopic.TODAY, setOf(OnboardingFact.OTHER_DAYS_VISIBLE)).size)
        assertEquals(5, OnboardingRules.stepsFor(OnboardingTopic.TODAY, setOf(OnboardingFact.OTHER_DAYS_VISIBLE, OnboardingFact.DO_NEXT_VISIBLE)).size)
    }

    @Test
    fun `food day in log-as-I-go mode has no checklist step but the log food step`() {
        val targets = OnboardingRules.stepsFor(OnboardingTopic.FOOD_DAY, setOf(OnboardingFact.FOOD_BUILDER_SHOWN)).map { it.target }
        assertEquals(false, OnboardingTarget.FOOD_CHECKLIST in targets)
        assertEquals(false, OnboardingTarget.FOOD_LOG_OTHER in targets)
        assertEquals(true, OnboardingTarget.FOOD_LOG_FOOD in targets)
    }

    @Test
    fun `food day in plan mode has the checklist and the picker step`() {
        val targets = OnboardingRules.stepsFor(OnboardingTopic.FOOD_DAY, setOf(OnboardingFact.FOOD_PLAN_SHOWN, OnboardingFact.FOOD_PICKER_SHOWN)).map { it.target }
        assertEquals(true, OnboardingTarget.FOOD_CHECKLIST in targets)
        assertEquals(true, OnboardingTarget.FOOD_LOG_OTHER in targets)
        assertEquals(false, OnboardingTarget.FOOD_LOG_FOOD in targets)
    }

    @Test
    fun `the builder tip comes first, the units tip only after the amount row opens`() {
        assertEquals(OnboardingTopic.MEAL_BUILDER, next(OnboardingScreen.MEAL_BUILDER))
        val builderSeen = seen(OnboardingTopic.MEAL_BUILDER)
        assertNull(next(OnboardingScreen.MEAL_BUILDER, builderSeen))
        assertEquals(
            OnboardingTopic.MEAL_BUILDER_AMOUNT,
            next(OnboardingScreen.MEAL_BUILDER, builderSeen, facts = setOf(OnboardingFact.AMOUNT_ROW_OPEN), factsAtVisitStart = emptySet(), shownThisVisit = setOf(OnboardingTopic.MEAL_BUILDER)),
        )
    }

    @Test
    fun `the units tip waits for the builder tip`() = assertEquals(
        OnboardingTopic.MEAL_BUILDER,
        next(OnboardingScreen.MEAL_BUILDER, facts = setOf(OnboardingFact.AMOUNT_ROW_OPEN)),
    )

    @Test
    fun `the create food tip shows once the create link is visible`() {
        val builderSeen = seen(OnboardingTopic.MEAL_BUILDER)
        assertNull(next(OnboardingScreen.MEAL_BUILDER, builderSeen))
        assertEquals(OnboardingTopic.CUSTOM_FOOD, next(OnboardingScreen.MEAL_BUILDER, builderSeen, setOf(OnboardingFact.CREATE_FOOD_VISIBLE), factsAtVisitStart = emptySet()))
    }

    @Test
    fun `the builder tip drops the recents step without recents`() {
        assertEquals(2, OnboardingRules.stepsFor(OnboardingTopic.MEAL_BUILDER, emptySet()).size)
        assertEquals(3, OnboardingRules.stepsFor(OnboardingTopic.MEAL_BUILDER, setOf(OnboardingFact.RECENTS_VISIBLE)).size)
    }
}
