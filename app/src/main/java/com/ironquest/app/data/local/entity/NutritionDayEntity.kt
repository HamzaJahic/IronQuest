package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-day state, created lazily on the first nutrition write for that day. The target
 * columns snapshot the active plan; they only matter once the day is past (today always
 * reads the live active plan).
 */
@Entity(tableName = "nutrition_days")
data class NutritionDayEntity(
    @PrimaryKey val dateEpochDay: Long,
    val planId: Long?,
    val targetProteinG: Double?,
    val targetCarbsG: Double?,
    val targetFatG: Double?,
    val targetFiberG: Double?,
    val targetKcal: Double?,
    val waterTargetMl: Int?,
    val waterMl: Int,
    val proteinBonusXp: Int,
    val waterBonusXp: Int,
)
