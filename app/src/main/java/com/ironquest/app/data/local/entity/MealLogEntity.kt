package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One eaten meal. Macros are a **1× snapshot** taken at log time so editing or deleting the
 * meal never rewrites history. Unique (dateEpochDay, slotId): a slot can be checked once per
 * day; NULL slotIds (off-plan) are distinct in SQLite, so off-plan logs are unlimited.
 */
@Entity(
    tableName = "meal_logs",
    foreignKeys = [
        ForeignKey(entity = PlanSlotEntity::class, parentColumns = ["id"], childColumns = ["slotId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index(value = ["dateEpochDay", "slotId"], unique = true), // not UI text: Room index column names
        Index("slotId"), // not UI text: Room index column names
        Index("mealId"), // not UI text: Room index column names
    ],
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val slotId: Long?,
    val mealId: Long?,
    val mealName: String,
    val portion: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    /** Always resolved (entered or derived) at log time. */
    val kcal: Double,
    val xpAwarded: Int,
    val loggedAtMillis: Long,
)

/** Per-day sums of meal_logs for the Week/Month views. A query result, not an entity: no schema change. */
data class DayTotalsRow(
    val dateEpochDay: Long,
    val logCount: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val kcal: Double,
)
