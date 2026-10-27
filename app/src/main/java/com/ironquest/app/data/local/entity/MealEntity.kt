package com.ironquest.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** User-owned meal. Seeded once (migration / onCreate), never re-upserted — unlike `exercises`. */
@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    /** null → derived 4P + 4C + 9F. */
    val kcal: Double?,
    val recipeText: String?,
    val recipeUrl: String?,
    /** Set instead of deleting when a plan slot still references the meal. */
    val archived: Boolean,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "meal_tags",
    primaryKeys = ["mealId", "tag"],
    foreignKeys = [
        ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag")],
)
data class MealTagEntity(val mealId: Long, val tag: String)

data class MealWithTags(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val tags: List<MealTagEntity>,
)

/** Flat row for "Used in" on the meal detail screen. */
data class SlotUsageRow(val planName: String, val label: String, val timeOfDayMin: Int?)
