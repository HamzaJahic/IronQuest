package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType

/** Exercise catalog. Rows are upserted from ProgramCatalog on every DB open. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stat: StatType,
    val trackingType: TrackingType,
    val incrementKg: Double,
)
