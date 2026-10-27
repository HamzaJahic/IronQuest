package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironquest.app.domain.food.UserFoodSource

/** Your own foods and the Open Food Facts products you picked. Macros per 100 g. */
@Entity(
    tableName = "user_foods",
    indices = [Index(value = ["barcode"], unique = true)], // not UI text: Room index column names
)
data class UserFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val kcal: Double?,
    val pieceGrams: Double?,
    val servingGrams: Double?,
    val source: UserFoodSource,
    /** Null for your own foods; SQLite treats NULLs as distinct, so the unique index allows many. */
    val barcode: String?,
    val createdAtMillis: Long,
)
