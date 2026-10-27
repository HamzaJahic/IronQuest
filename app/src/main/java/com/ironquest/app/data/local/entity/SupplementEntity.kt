package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "supplements")
data class SupplementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    /** Optional reminder time (minutes from midnight). */
    val reminderTimeMin: Int?,
    val archived: Boolean,
    val position: Int,
    /**
     * v3. Day the supplement was added (adherence counts from here). Null for supplements that
     * predate v3 and had no intakes; the migration backfilled the rest from their first intake.
     */
    val createdAtEpochDay: Long? = null,
)
