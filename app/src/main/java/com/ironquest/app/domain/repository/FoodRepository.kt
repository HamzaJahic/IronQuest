package com.ironquest.app.domain.repository

import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.food.Recents
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun observeUserFoods(): Flow<List<UserFood>>

    suspend fun userFoods(): List<UserFood>

    suspend fun getUserFood(id: Long): UserFood?

    /** Validates name (non-blank) and macros (each 0..MAX_MEAL_GRAMS per 100 g, kcal ≤ MAX_KCAL). Returns the id. */
    suspend fun saveUserFood(draft: UserFoodDraft): Long

    /** Past logs keep their snapshots; the food leaves search and recents. */
    suspend fun deleteUserFood(id: Long)

    /** Inserts or refreshes the product by barcode, so it works offline and in recents. */
    suspend fun saveOnlineFood(food: OnlineFood): UserFood

    /** Latest line per food, newest first. */
    fun observeRecentFoods(limit: Int = Recents.LIMIT): Flow<List<MealLogItem>>

    /** Newest quick meals with their items, de-duplicated by contents. */
    fun observeRecentMeals(limit: Int = Recents.LIMIT): Flow<List<MealLogEntry>>
}
