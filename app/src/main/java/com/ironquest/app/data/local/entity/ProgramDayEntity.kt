package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** v4. One workout day of an imported program; its template id is `"C$id"` (Program.customTemplateId). */
@Entity(
    tableName = "program_days",
    foreignKeys = [
        ForeignKey(
            entity = ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"], // not UI text: Room index and foreign key column names
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("programId")], // not UI text: Room index and foreign key column names
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val name: String,
    val position: Int,
)
