package com.ironquest.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "plan_slots",
    foreignKeys = [
        ForeignKey(entity = MealPlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["mealId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("planId"), Index("mealId")], // not UI text: Room index column names
)
data class PlanSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val label: String,
    val mealId: Long,
    val timeOfDayMin: Int?,
    val reminderOffsetMin: Int?,
    val position: Int,
)

data class SlotWithMeal(
    @Embedded val slot: PlanSlotEntity,
    @Relation(parentColumn = "mealId", entityColumn = "id")
    val meal: MealEntity,
)
