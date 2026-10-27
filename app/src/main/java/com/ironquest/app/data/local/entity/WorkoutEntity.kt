package com.ironquest.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One performed session of a workout template ("Upper 1" on 2026-09-29).
 * A row with `completedAtMillis == null` is an in-progress workout that can be resumed.
 */
@Entity(
    tableName = "workouts",
    indices = [
        Index("templateId", "completedAtMillis"), // not UI text: Room index and foreign key column names
        Index("dateEpochDay"), // not UI text: Room index and foreign key column names
    ],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: String,
    /** LocalDate.toEpochDay() in the device zone at start time; what the calendar keys on. */
    val dateEpochDay: Long,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    /** Set XP + completion bonus, filled in on completion. */
    val xpEarned: Int = 0,
)

data class WorkoutWithSets(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sets: List<SetEntity>,
)

/** `WorkoutDao.observeLastCompletion`; the query only returns completed rows, so the time is non-null. */
data class LastCompletionRow(val templateId: String, val completedAtMillis: Long)
