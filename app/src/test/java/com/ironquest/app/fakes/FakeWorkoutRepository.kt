package com.ironquest.app.fakes

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
import com.ironquest.app.domain.model.StatProgress
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.WorkoutCompleteResult
import com.ironquest.app.domain.model.WorkoutLogDraft
import com.ironquest.app.domain.progression.LevelCurve
import com.ironquest.app.domain.progression.XpRules
import com.ironquest.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** In-memory repository for ViewModel tests. Mirrors the XP rules of the real one. */
class FakeWorkoutRepository : WorkoutRepository {

    val ghosts = MutableStateFlow<Map<String, GhostSession>>(emptyMap())
    var inProgress: InProgressSession? = null
    val totalXp = MutableStateFlow(0L)
    val completedDays = MutableStateFlow<Set<LocalDate>>(emptySet())

    /** Extra completed workouts for the week streak (one entry per workout); completedDays count too. */
    val completedDates = MutableStateFlow<List<LocalDate>>(emptyList())

    /** Goal types passed to each completeWorkout call. */
    val completedGoalTypes = mutableListOf<Set<GoalType>>()

    val logRequests = mutableListOf<LogSetRequest>()
    val updates = mutableListOf<Pair<Long, LoggedSet>>()
    val completed = mutableListOf<Long>()
    var failNextLog = false

    private var nextWorkoutId = 100L
    private var nextSetId = 1L
    private val xpByWorkout = mutableMapOf<Long, Int>()

    /** Live sets: set id → workout id. */
    private val setWorkouts = mutableMapOf<Long, Long>()
    val deletedSets = mutableListOf<Long>()

    override fun observeProfile(): Flow<PlayerProfile> = totalXp.map { xp ->
        PlayerProfile(
            totalXp = xp,
            level = LevelCurve.CHARACTER.progressFor(xp),
            stats = StatType.entries.map { StatProgress(it, 0, LevelCurve.STAT.progressFor(0)) },
            questsCompleted = completed.size,
        )
    }

    override fun observeInProgressTemplateId(): Flow<String?> = flowOf(inProgress?.templateId)
    val lastCompletion = MutableStateFlow<LastCompletion?>(null)
    override fun observeLastCompletion(): Flow<LastCompletion?> = lastCompletion
    override fun observeGhost(templateId: String): Flow<GhostSession?> = ghosts.map { it[templateId] }
    override suspend fun findInProgress(templateId: String): InProgressSession? = inProgress?.takeIf { it.templateId == templateId }

    override suspend fun logSet(request: LogSetRequest): LogSetResult {
        if (failNextLog) {
            failNextLog = false
            error("disk full")
        }
        logRequests += request
        val workoutId = request.workoutId ?: nextWorkoutId++
        val xp = XpRules.forSet(request.set, request.ghost, request.exercise.exercise.trackingType)
        xpByWorkout[workoutId] = (xpByWorkout[workoutId] ?: 0) + xp.total
        totalXp.value += xp.total
        val setId = nextSetId++
        setWorkouts[setId] = workoutId
        return LogSetResult(setId = setId, workoutId = workoutId, xp = xp, levelUp = null)
    }

    override suspend fun updateSet(setId: Long, set: LoggedSet) {
        updates += setId to set
    }

    override suspend fun deleteSet(setId: Long): Boolean {
        val workoutId = setWorkouts.remove(setId) ?: return false
        deletedSets += setId
        return workoutId !in setWorkouts.values
    }

    override suspend fun completeWorkout(workoutId: Long, goalTypes: Set<GoalType>): WorkoutCompleteResult {
        completed += workoutId
        completedGoalTypes += goalTypes
        totalXp.value += XpRules.QUEST_COMPLETE_BONUS
        return WorkoutCompleteResult(
            bonusXp = XpRules.QUEST_COMPLETE_BONUS,
            totalXpEarned = (xpByWorkout[workoutId] ?: 0) + XpRules.QUEST_COMPLETE_BONUS,
            levelUp = null,
        )
    }

    override fun observeCompletedDays(month: YearMonth): Flow<Set<LocalDate>> = completedDays.map { days -> days.filterTo(mutableSetOf()) { YearMonth.from(it) == month } }

    override fun observeCompletedCount(from: LocalDate, to: LocalDate): Flow<Int> = completedDays.map { days -> days.count { !it.isBefore(from) && !it.isAfter(to) } }

    override fun observeCompletedDates(from: LocalDate, to: LocalDate): Flow<List<LocalDate>> = combine(completedDays, completedDates) { days, dates ->
        (days.toList() + dates).filter { !it.isBefore(from) && !it.isAfter(to) }
    }

    override fun observeDayLog(date: LocalDate): Flow<List<CompletedWorkoutLog>> = flowOf(emptyList())

    /** Logs by workout id for the past-workout editor. */
    val workoutLogs = mutableMapOf<Long, CompletedWorkoutLog>()
    val savedLogs = mutableListOf<WorkoutLogDraft>()
    val deleted = mutableListOf<Long>()

    override suspend fun findWorkoutLog(workoutId: Long): CompletedWorkoutLog? = workoutLogs[workoutId]

    override suspend fun ghostFor(templateId: String, date: LocalDate, excludeWorkoutId: Long?): GhostSession? = ghosts.value[templateId]

    override suspend fun saveWorkoutLog(draft: WorkoutLogDraft, goalTypes: Set<GoalType>): SaveWorkoutResult {
        savedLogs += draft
        return SaveWorkoutResult(draft.workoutId ?: nextWorkoutId++, xpDelta = 0, levelUp = null)
    }

    override suspend fun deleteWorkout(workoutId: Long) {
        deleted += workoutId
    }
}
