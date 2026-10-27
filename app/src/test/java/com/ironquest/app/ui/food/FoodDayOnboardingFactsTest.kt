package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.FoodTrackingMode
import com.ironquest.app.domain.nutrition.SupplementStatus
import com.ironquest.app.domain.onboarding.OnboardingFact
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FoodDayOnboardingFactsTest {
    private val date = LocalDate.of(2026, 10, 26)

    private fun state(mode: FoodTrackingMode, supplements: Boolean = false) = FoodDayUiState(
        date = date,
        mode = mode,
        supplements = if (supplements) listOf(SupplementStatus(1, "Creatine", "5 g", taken = false, streak = 0)) else emptyList(),
    )

    @Test
    fun `plan mode shows the plan and the picker link`() = assertEquals(
        setOf(OnboardingFact.FOOD_PLAN_SHOWN, OnboardingFact.FOOD_PICKER_SHOWN),
        state(FoodTrackingMode.PLAN).onboardingFacts(dialogOpen = false),
    )

    @Test
    fun `on the fly mode shows only the builder link`() = assertEquals(
        setOf(OnboardingFact.FOOD_BUILDER_SHOWN),
        state(FoodTrackingMode.ON_THE_FLY).onboardingFacts(dialogOpen = false),
    )

    @Test
    fun `both mode shows the plan and the builder link`() = assertEquals(
        setOf(OnboardingFact.FOOD_PLAN_SHOWN, OnboardingFact.FOOD_BUILDER_SHOWN),
        state(FoodTrackingMode.BOTH).onboardingFacts(dialogOpen = false),
    )

    @Test
    fun `supplements and an open dialog are reported`() = assertEquals(
        setOf(OnboardingFact.SUPPLEMENTS_VISIBLE, OnboardingFact.FOOD_PLAN_SHOWN, OnboardingFact.FOOD_PICKER_SHOWN, OnboardingFact.BLOCKED),
        state(FoodTrackingMode.PLAN, supplements = true).onboardingFacts(dialogOpen = true),
    )
}
