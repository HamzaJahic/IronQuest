package com.ironquest.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType

@Entity(
    tableName = "sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"], // not UI text: Room index and foreign key column names
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
        Index(value = ["workoutId", "exerciseId", "setIndex"], unique = true), // not UI text: Room index and foreign key column names
        Index("exerciseId"), // not UI text: Room index and foreign key column names
    ],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: String,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    /** Only for DURATION exercises (cardio). */
    val durationMinutes: Int? = null,
    /** Snapshot of the prescription at log time, so history stays correct if the program changes. */
    val targetRepMin: Int,
    val targetRepMax: Int,
    val xpAwarded: Int,
    val loggedAtMillis: Long,
)

/** Flat join row for the calendar day log. */
data class SetDetailRow(
    val workoutId: Long,
    val templateId: String,
    val dateEpochDay: Long,
    val completedAtMillis: Long,
    val workoutXp: Int,
    val exerciseId: String,
    val exerciseName: String,
    val stat: StatType,
    val trackingType: TrackingType,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val durationMinutes: Int?,
    val setId: Long,
    val targetRepMin: Int,
    val targetRepMax: Int,
)

/** XP a logged set paid and the stat it fed; used to take that XP back. */
data class SetStatXp(val xpAwarded: Int, val stat: StatType)
