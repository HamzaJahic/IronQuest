package com.ironquest.app.data.repository

import androidx.room.withTransaction
import com.ironquest.app.data.local.IronQuestDatabase
import com.ironquest.app.data.local.entity.MealLogItemEntity
import com.ironquest.app.data.local.entity.UserFoodEntity
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.food.Recents
import com.ironquest.app.domain.food.UserFood
import com.ironquest.app.domain.food.UserFoodDraft
import com.ironquest.app.domain.food.UserFoodSource
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealLogEntry
import com.ironquest.app.domain.nutrition.MealLogItem
import com.ironquest.app.domain.nutrition.NutritionRules
import com.ironquest.app.domain.repository.FoodRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineFoodRepository @Inject constructor(
    private val db: IronQuestDatabase,
    private val clock: Clock,
) : FoodRepository {

    private val foodDao = db.foodDao()

    override fun observeUserFoods(): Flow<List<UserFood>> = foodDao.observeUserFoods().map { rows -> rows.map { it.toDomain() } }

    override suspend fun userFoods(): List<UserFood> = foodDao.allUserFoods().map { it.toDomain() }

    override suspend fun getUserFood(id: Long): UserFood? = foodDao.getUserFood(id)?.toDomain()

    override suspend fun saveUserFood(draft: UserFoodDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank()) { "Food name is required" }
        val macros = draft.per100g
        listOf(macros.proteinG, macros.carbsG, macros.fatG, macros.fiberG).forEach { grams ->
            require(grams in 0.0..NutritionRules.MAX_MEAL_GRAMS) { "Macro out of range" }
        }
        macros.kcal?.let { kcal -> require(kcal in 0.0..NutritionRules.MAX_KCAL) { "Calories out of range" } }
        draft.pieceGrams?.let { grams -> require(grams > 0 && grams <= NutritionRules.MAX_MEAL_GRAMS) { "Piece weight out of range" } }
        if (draft.id == null) {
            foodDao.insertUserFood(
                UserFoodEntity(
                    name = draft.name.trim(),
                    brand = null,
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    fiberG = macros.fiberG,
                    kcal = macros.kcal,
                    pieceGrams = draft.pieceGrams,
                    servingGrams = null,
                    source = UserFoodSource.CUSTOM,
                    barcode = null,
                    createdAtMillis = clock.millis(),
                ),
            )
        } else {
            val existing = foodDao.getUserFood(draft.id) ?: error("Food ${draft.id} not found")
            foodDao.updateUserFood(
                existing.copy(
                    name = draft.name.trim(),
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    fiberG = macros.fiberG,
                    kcal = macros.kcal,
                    pieceGrams = draft.pieceGrams,
                ),
            )
            draft.id
        }
    }

    override suspend fun deleteUserFood(id: Long) = foodDao.deleteUserFood(id)

    override suspend fun saveOnlineFood(food: OnlineFood): UserFood = db.withTransaction {
        val existing = foodDao.findByBarcode(food.barcode)
        val macros = food.per100g
        val entity = if (existing == null) {
            val fresh = UserFoodEntity(
                name = food.name,
                brand = food.brand,
                proteinG = macros.proteinG,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                fiberG = macros.fiberG,
                kcal = macros.kcal,
                pieceGrams = null,
                servingGrams = food.servingGrams,
                source = UserFoodSource.ONLINE,
                barcode = food.barcode,
                createdAtMillis = clock.millis(),
            )
            fresh.copy(id = foodDao.insertUserFood(fresh))
        } else {
            val refreshed = existing.copy(
                name = food.name,
                brand = food.brand,
                proteinG = macros.proteinG,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                fiberG = macros.fiberG,
                kcal = macros.kcal,
                servingGrams = food.servingGrams,
            )
            foodDao.updateUserFood(refreshed)
            refreshed
        }
        entity.toDomain()
    }

    override fun observeRecentFoods(limit: Int): Flow<List<MealLogItem>> = foodDao.observeRecentItems(limit).map { rows -> rows.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeRecentMeals(limit: Int): Flow<List<MealLogEntry>> = foodDao.observeRecentQuickLogs(limit * RECENT_OVERSAMPLE).mapLatest { logs ->
        val itemsByLog = foodDao.itemsForLogs(logs.map { it.id }).groupBy { it.logId }
        Recents.distinctMeals(logs.map { log -> log.toDomain(itemsByLog[log.id].orEmpty().map(MealLogItemEntity::toDomain)) }, limit)
    }

    private companion object {
        /** Repeats collapse in [Recents.distinctMeals], so read more rows than are shown. */
        const val RECENT_OVERSAMPLE = 3
    }
}

private fun UserFoodEntity.toDomain() = UserFood(
    id = id,
    name = name,
    per100g = Macros(proteinG, carbsG, fatG, fiberG, kcal),
    pieceGrams = pieceGrams,
    servingGrams = servingGrams,
    source = source,
    barcode = barcode,
    brand = brand,
)
