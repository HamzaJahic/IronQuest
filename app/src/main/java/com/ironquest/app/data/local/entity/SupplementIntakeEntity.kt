package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "supplement_intakes",
    primaryKeys = ["supplementId", "dateEpochDay"], // not UI text: Room index column names
    foreignKeys = [
        ForeignKey(entity = SupplementEntity::class, parentColumns = ["id"], childColumns = ["supplementId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SupplementIntakeEntity(
    val supplementId: Long,
    val dateEpochDay: Long,
    val xpAwarded: Int,
    val loggedAtMillis: Long,
)
