package com.ironquest.app.domain.repository

import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.model.CompletedWorkoutLog
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.InProgressSession
import com.ironquest.app.domain.model.LastCompletion
import com.ironquest.app.domain.model.LogSetRequest
import com.ironquest.app.domain.model.LogSetResult
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.PlayerProfile
import com.ironquest.app.domain.model.SaveWorkoutResult
import com.ironquest.app.domain.model.WorkoutCompleteResult
import com.ironquest.app.domain.model.WorkoutLogDraft
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

interface WorkoutRepository {
    fun observeProfile(): Flow<PlayerProfile>

    /** Template id of any unfinished session (Today's "Resume workout"), or null. */
    fun observeInProgressTemplateId(): Flow<String?>

    /** Latest finished workout by completion time (a Log entry counts at its stamp: noon of its day, or now for today). */
    fun observeLastCompletion(): Flow<LastCompletion?>

    fun observeGhost(templateId: String): Flow<GhostSession?>

    suspend fun findInProgress(templateId: String): InProgressSession?

    /** Persists a set and awards its XP atomically. */
    suspend fun logSet(request: LogSetRequest): LogSetResult

    /** Corrects an already-logged set. Never re-awards XP. */
    suspend fun updateSet(setId: Long, set: LoggedSet)

    /**
     * Removes a set of an unfinished session and takes back the XP it paid (floored at 0).
     * Returns true when that was the session's last set: the empty session is removed too.
     * A missing set, or one of a finished workout, is left alone (false).
     */
    suspend fun deleteSet(setId: Long): Boolean

    /**
     * Marks the session complete and awards the completion bonus plus the bonuses of the
     * workout goals reached, for the goal types in [goalTypes] (those the user shows). Idempotent.
     */
    suspend fun completeWorkout(workoutId: Long, goalTypes: Set<GoalType>): WorkoutCompleteResult

    fun observeCompletedDays(month: YearMonth): Flow<Set<LocalDate>>

    fun observeCompletedCount(from: LocalDate, to: LocalDate): Flow<Int>

    /** Start date of every completed workout in [from]..[to] (repeats when a day has several). */
    fun observeCompletedDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>>

    fun observeDayLog(date: LocalDate): Flow<List<CompletedWorkoutLog>>

    /** One finished workout as the day log shows it, or null if it is gone or unfinished. */
    suspend fun findWorkoutLog(workoutId: Long): CompletedWorkoutLog?

    /**
     * "Last time" for a session of [templateId] on [date]: the latest finished session that started
     * before it (leaving out [excludeWorkoutId], the one being edited).
     */
    suspend fun ghostFor(templateId: String, date: LocalDate, excludeWorkoutId: Long? = null): GhostSession?

    /**
     * Writes a finished session for today or an earlier day, or rewrites a stored one. XP is paid
     * like a live session (sets, ghost bonus, goals for [goalTypes], completion bonus); a rewrite
     * pays or takes back only the difference in set XP. Throws [IllegalArgumentException] for a
     * future date or no sets.
     */
    suspend fun saveWorkoutLog(draft: WorkoutLogDraft, goalTypes: Set<GoalType>): SaveWorkoutResult

    /** Removes a workout and takes back the XP it paid (floored at 0). A missing workout is a no-op. */
    suspend fun deleteWorkout(workoutId: Long)
}
