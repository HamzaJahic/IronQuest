package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.fakes.FakeBasicFoodSource
import com.ironquest.app.fakes.FakeFoodRepository
import com.ironquest.app.fakes.FakeNutritionRepository
import com.ironquest.app.fakes.FakeOnlineFoodSearch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FoodSearchUseCaseTest {
    private val nutrition = FakeNutritionRepository()
    private val foodRepository = FakeFoodRepository()
    private val basic = FakeBasicFoodSource()
    private val onlineSearch = FakeOnlineFoodSearch()
    private val useCase = FoodSearchUseCase(nutrition, foodRepository, basic, onlineSearch)

    private fun meal(id: Long, name: String) = Meal(id, name, Macros(proteinG = 10.0), tags = emptyList(), recipeText = null, recipeUrl = null)

    private fun userFood(id: Long, name: String) = UserFood(id, name, Macros(proteinG = 5.0), pieceGrams = null, servingGrams = null, UserFoodSource.CUSTOM, barcode = null, brand = null)

    private fun builtIn(id: Long, name: String) = BasicFood(id, name, aliases = emptyList(), per100g = Macros(proteinG = 12.0), units = listOf(FoodUnit.GRAM))

    @Test
    fun `library meals are listed separately and your foods come before built-in`() = runTest {
        nutrition.meals.value = listOf(meal(1, "Egg wrap"), meal(2, "Pasta"))
        foodRepository.userFoods.value = listOf(userFood(7, "Egg white"))
        basic.foods = listOf(builtIn(1, "Egg"))

        val results = useCase.local("egg")

        assertEquals(listOf("Egg wrap"), results.meals.map { it.name })
        assertEquals(listOf("Egg white", "Egg"), results.foods.map { it.name })
        assertEquals(3, results.count)
    }

    @Test
    fun `no match is empty`() = runTest {
        basic.foods = listOf(builtIn(1, "Egg"))
        assertEquals(true, useCase.local("zzz").isEmpty)
    }

    @Test
    fun `resolve finds built-in and your foods, nothing else`() = runTest {
        foodRepository.userFoods.value = listOf(userFood(7, "Skyr"))
        basic.foods = listOf(builtIn(1, "Egg"))

        assertEquals("Egg", useCase.resolve("b:1")?.name)
        assertEquals("Skyr", useCase.resolve("u:7")?.name)
        assertNull(useCase.resolve("u:404"))
        assertNull(useCase.resolve("m:1"))
        assertNotNull(useCase.resolve(FoodKey.builtIn(1)))
    }

    @Test
    fun `online drops products you already have by barcode and passes failures on`() = runTest {
        foodRepository.userFoods.value = listOf(userFood(7, "Skyr").copy(barcode = "111"))
        val owned = OnlineFood("111", "Skyr", "Arla", Macros(proteinG = 11.0), servingGrams = null)
        val other = OnlineFood("222", "Skyr vanilla", "Arla", Macros(proteinG = 10.0), servingGrams = 150.0)
        onlineSearch.respond = { Result.success(listOf(owned, other)) }
        assertEquals(listOf("222"), useCase.online("skyr").getOrThrow().map { it.barcode })

        onlineSearch.respond = { Result.failure(FoodSearchException(FoodSearchError.OFFLINE)) }
        assertEquals(FoodSearchError.OFFLINE, (useCase.online("skyr").exceptionOrNull() as FoodSearchException).error)
    }
}
