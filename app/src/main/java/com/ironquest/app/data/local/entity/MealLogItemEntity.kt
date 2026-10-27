package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One food of a quick meal. Snapshot like meal_logs: name and macros for [amount] at log time, so
 * editing or deleting the food never rewrites history. No foreign key to user_foods on purpose.
 */
@Entity(
    tableName = "meal_log_items",
    foreignKeys = [ForeignKey(entity = MealLogEntity::class, parentColumns = ["id"], childColumns = ["logId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index("logId"), // not UI text: Room index column names
        Index("foodKey"), // not UI text: Room index column names
    ],
)
data class MealLogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val position: Int,
    val foodKey: String,
    val foodName: String,
    val amount: Double,
    val unitKey: String,
    val gramsPerUnit: Double?,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val kcal: Double,
)
