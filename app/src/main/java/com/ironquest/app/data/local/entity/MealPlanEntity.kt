package com.ironquest.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/** At most one row has `isActive = 1`; enforced by OfflineNutritionRepository.setActivePlan. */
@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean,
    val targetProteinG: Double?,
    val targetCarbsG: Double?,
    val targetFatG: Double?,
    val targetFiberG: Double?,
    val targetKcal: Double?,
    val waterTargetMl: Int?,
    val position: Int,
)

data class PlanWithSlots(
    @Embedded val plan: MealPlanEntity,
    @Relation(entity = PlanSlotEntity::class, parentColumn = "id", entityColumn = "planId")
    val slots: List<SlotWithMeal>,
)
