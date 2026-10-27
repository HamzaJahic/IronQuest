package com.ironquest.app.fakes

import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.food.UserFoodSource
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.repository.FoodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory FoodRepository; tests set the flows directly. */
class FakeFoodRepository : FoodRepository {
    val userFoods = MutableStateFlow<List<UserFood>>(emptyList())
    val recentFoods = MutableStateFlow<List<MealLogItem>>(emptyList())
    val recentMeals = MutableStateFlow<List<MealLogEntry>>(emptyList())

    val saved = mutableListOf<UserFoodDraft>()
    val savedOnline = mutableListOf<OnlineFood>()
    val deleted = mutableListOf<Long>()

    private var nextId = 1L

    override fun observeUserFoods(): Flow<List<UserFood>> = userFoods

    override suspend fun userFoods(): List<UserFood> = userFoods.value

    override suspend fun getUserFood(id: Long): UserFood? = userFoods.value.firstOrNull { it.id == id }

    override suspend fun saveUserFood(draft: UserFoodDraft): Long {
        saved += draft
        val id = draft.id ?: nextId++
        val food = UserFood(id, draft.name.trim(), draft.per100g, draft.pieceGrams, servingGrams = null, UserFoodSource.CUSTOM, barcode = null, brand = null)
        userFoods.update { list -> list.filterNot { it.id == id } + food }
        return id
    }

    override suspend fun deleteUserFood(id: Long) {
        deleted += id
        userFoods.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun saveOnlineFood(food: OnlineFood): UserFood {
        savedOnline += food
        val existing = userFoods.value.firstOrNull { it.barcode == food.barcode }
        val saved = UserFood(existing?.id ?: nextId++, food.name, food.per100g, existing?.pieceGrams, food.servingGrams, UserFoodSource.ONLINE, food.barcode, food.brand)
        userFoods.update { list -> list.filterNot { it.id == saved.id } + saved }
        return saved
    }

    override fun observeRecentFoods(limit: Int): Flow<List<MealLogItem>> = recentFoods.map { it.take(limit) }

    override fun observeRecentMeals(limit: Int): Flow<List<MealLogEntry>> = recentMeals.map { it.take(limit) }
}
