package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.repository.BasicFoodSource
import com.ironquest.app.domain.repository.FoodRepository
import com.ironquest.app.domain.repository.NutritionRepository
import com.ironquest.app.domain.repository.OnlineFoodSearch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Results that need no network, in display order. */
data class LocalFoodResults(val meals: List<Meal>, val foods: List<FoodChoice>) {
    val isEmpty: Boolean get() = meals.isEmpty() && foods.isEmpty()
    val count: Int get() = meals.size + foods.size
}

/** Coordinates the meal library, your foods and the built-in list and Open Food Facts. */
class FoodSearchUseCase @Inject constructor(
    private val nutrition: NutritionRepository,
    private val foods: FoodRepository,
    private val basic: BasicFoodSource,
    private val online: OnlineFoodSearch,
) {
    suspend fun local(query: String): LocalFoodResults {
        val meals = FoodSearchRanker.rank(query, nutrition.observeMeals().first(), { it.name }, { it.tags }, MEAL_LIMIT)
        val mine = FoodSearchRanker.rank(query, foods.userFoods(), { it.name }, { listOfNotNull(it.brand) }, MINE_LIMIT).map { it.toChoice() }
        val builtIn = FoodSearchRanker.rank(query, basic.all(), { it.name }, { it.aliases }, BUILT_IN_LIMIT).map { it.toChoice() }
        return LocalFoodResults(meals, mine + builtIn)
    }

    /** Open Food Facts products, minus those already saved in your foods (they show under "Your foods"). */
    suspend fun online(query: String): Result<List<OnlineFood>> {
        val result = online.search(query)
        if (result.isFailure) return result
        val owned = foods.userFoods().mapNotNull { it.barcode }.toSet()
        return result.map { products -> products.filterNot { it.barcode in owned } }
    }

    /** A recent line's food again, with all its units; null when the food is gone (then the snapshot is used). */
    suspend fun resolve(key: String): FoodChoice? {
        FoodKey.builtInId(key)?.let { id -> return basic.all().firstOrNull { it.usdaId == id }?.toChoice() }
        FoodKey.mineId(key)?.let { id -> return foods.getUserFood(id)?.toChoice() }
        return null
    }

    companion object {
        const val MEAL_LIMIT = 5
        const val MINE_LIMIT = 10
        const val BUILT_IN_LIMIT = 30
    }
}
