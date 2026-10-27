package com.ironquest.app.ui.food.builder

import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.LocalFoodResults
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.onboarding.OnboardingFact
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MealBuilderOnboardingFactsTest {
    private val base = MealBuilderUiState(isLoading = false, date = LocalDate.of(2026, 10, 26))
    private val recentFood = MealLogItem("rice", "Rice", 100.0, FoodUnit.GRAM, Macros(3.0, 28.0, 0.5))
    private val pending = PendingFood(choice = null, snapshot = recentFood, unit = FoodUnit.GRAM, amountText = "100")
    private val local = BuilderSearch.Local(LocalFoodResults(emptyList(), emptyList()))

    @Test
    fun `an empty builder with no history reports nothing`() = assertEquals(emptySet<OnboardingFact>(), base.onboardingFacts(dialogOpen = false))

    @Test
    fun `recents are visible only with an empty query and no pending food`() {
        val withRecents = base.copy(recentFoods = listOf(recentFood))
        assertEquals(setOf(OnboardingFact.RECENTS_VISIBLE), withRecents.onboardingFacts(dialogOpen = false))
        assertEquals(emptySet<OnboardingFact>(), withRecents.copy(query = "ri").onboardingFacts(dialogOpen = false))
    }

    @Test
    fun `a pending food opens the amount row`() = assertEquals(
        setOf(OnboardingFact.AMOUNT_ROW_OPEN),
        base.copy(pending = pending).onboardingFacts(dialogOpen = false),
    )

    @Test
    fun `the create link needs a query with local results state`() {
        assertEquals(setOf(OnboardingFact.CREATE_FOOD_VISIBLE), base.copy(query = "zzzz", search = local).onboardingFacts(dialogOpen = false))
        assertEquals(emptySet<OnboardingFact>(), base.copy(query = "zzzz").onboardingFacts(dialogOpen = false))
        assertEquals(emptySet<OnboardingFact>(), base.copy(query = " ", search = local).onboardingFacts(dialogOpen = false))
    }

    @Test
    fun `dialogs and the portion prompt block tips`() {
        assertEquals(setOf(OnboardingFact.BLOCKED), base.onboardingFacts(dialogOpen = true))
        val meal = Meal(1, "Oats", Macros(35.0, 75.0, 9.0), emptyList(), null, null)
        assertEquals(setOf(OnboardingFact.BLOCKED), base.copy(pendingMeal = meal).onboardingFacts(dialogOpen = false))
    }
}
