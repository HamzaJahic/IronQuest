package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** v4. An imported training program; at most one row has `isActive = 1` (none = built-in split). */
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean,
    val createdAtMillis: Long,
)
