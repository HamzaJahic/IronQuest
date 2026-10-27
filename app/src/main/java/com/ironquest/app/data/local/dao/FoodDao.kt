package com.ironquest.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ironquest.app.data.local.entity.MealLogEntity
import com.ironquest.app.data.local.entity.MealLogItemEntity
import com.ironquest.app.data.local.entity.UserFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM user_foods ORDER BY name COLLATE NOCASE")
    fun observeUserFoods(): Flow<List<UserFoodEntity>>

    @Query("SELECT * FROM user_foods")
    suspend fun allUserFoods(): List<UserFoodEntity>

    @Query("SELECT * FROM user_foods WHERE id = :id")
    suspend fun getUserFood(id: Long): UserFoodEntity?

    @Query("SELECT * FROM user_foods WHERE barcode = :barcode")
    suspend fun findByBarcode(barcode: String): UserFoodEntity?

    @Insert
    suspend fun insertUserFood(food: UserFoodEntity): Long

    @Update
    suspend fun updateUserFood(food: UserFoodEntity)

    @Query("DELETE FROM user_foods WHERE id = :id")
    suspend fun deleteUserFood(id: Long)

    @Insert
    suspend fun insertItems(items: List<MealLogItemEntity>)

    @Query("DELETE FROM meal_log_items WHERE logId = :logId")
    suspend fun deleteItems(logId: Long)

    @Query("SELECT * FROM meal_log_items WHERE logId = :logId ORDER BY position")
    suspend fun itemsFor(logId: Long): List<MealLogItemEntity>

    @Query(
        """
        SELECT i.* FROM meal_log_items i INNER JOIN meal_logs l ON l.id = i.logId
        WHERE l.dateEpochDay = :day ORDER BY i.logId, i.position
        """,
    )
    fun observeItemsForDay(day: Long): Flow<List<MealLogItemEntity>>

    /** Latest line per food (by id, which grows with time). */
    @Query("SELECT * FROM meal_log_items WHERE id IN (SELECT MAX(id) FROM meal_log_items GROUP BY foodKey) ORDER BY id DESC LIMIT :limit")
    fun observeRecentItems(limit: Int): Flow<List<MealLogItemEntity>>

    /** Newest quick meals; the caller de-duplicates (Recents.distinctMeals), so read a few more than shown. */
    @Query("SELECT * FROM meal_logs WHERE id IN (SELECT DISTINCT logId FROM meal_log_items) ORDER BY loggedAtMillis DESC, id DESC LIMIT :limit")
    fun observeRecentQuickLogs(limit: Int): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_log_items WHERE logId IN (:logIds) ORDER BY logId, position")
    suspend fun itemsForLogs(logIds: List<Long>): List<MealLogItemEntity>
}
