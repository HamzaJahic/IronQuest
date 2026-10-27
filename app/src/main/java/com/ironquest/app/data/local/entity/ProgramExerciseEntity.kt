package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType

/** v4. Prescription of one exercise in an imported day. */
@Entity(
    tableName = "program_exercises",
    foreignKeys = [
        ForeignKey(
            entity = ProgramDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"], // not UI text: Room index and foreign key column names
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"], // not UI text: Room index and foreign key column names
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["dayId", "exerciseId"], unique = true), // not UI text: Room index and foreign key column names
        Index("exerciseId"), // not UI text: Room index and foreign key column names
    ],
)
data class ProgramExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val exerciseId: String,
    val targetSets: Int,
    val repMin: Int,
    val repMax: Int,
    val position: Int,
    /** v5. Superset key, unique within the day (consecutive equal keys = one superset); null = standalone. */
    val supersetGroup: String? = null,
)

/** Flat row for loading imported programs: one per (day, exercise); days without exercises have null exercise columns. */
data class ProgramExerciseRow(
    val programId: Long,
    val programName: String,
    val programActive: Boolean,
    val dayId: Long?,
    val dayName: String?,
    val dayPosition: Int?,
    val exerciseId: String?,
    val exerciseName: String?,
    val stat: StatType?,
    val trackingType: TrackingType?,
    val incrementKg: Double?,
    val targetSets: Int?,
    val repMin: Int?,
    val repMax: Int?,
    val position: Int?,
    val supersetGroup: String?,
)
