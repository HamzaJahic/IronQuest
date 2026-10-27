package com.ironquest.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table (id = 1) holding the character sheet. */
@Entity(tableName = "player_profile")
data class PlayerProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val totalXp: Long = 0,
    val constitutionXp: Long = 0,
    val strengthXp: Long = 0,
    val staminaXp: Long = 0,
    val questsCompleted: Int = 0,
    /**
     * v3. Highest character level ever reached; character XP can drop (un-logging meals), so a
     * level-up is only reported above this. Never lowered.
     */
    @ColumnInfo(defaultValue = "1") val highestLevelReached: Int = 1,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
