package com.ironquest.app.domain.workout

import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.InProgressSession
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.progression.ProgressiveOverloadRules
import com.ironquest.app.domain.progression.formatKg

/** The exercises of a live workout. Pure: every change returns a new session. */
data class WorkoutSession(val exercises: List<SessionExercise> = emptyList()) {

    val loggedSets: Int get() = exercises.sumOf { exercise -> exercise.loggedCount }
    val targetSets: Int get() = exercises.sumOf { exercise -> exercise.definition.targetSets }
    val earnedXp: Int get() = exercises.sumOf { exercise -> exercise.rows.sumOf { row -> row.xp ?: 0 } }

    fun exercise(exerciseId: String): SessionExercise? = exercises.firstOrNull { exercise -> exercise.id == exerciseId }

    fun exercisesOf(block: WorkoutBlock): List<SessionExercise> = block.exercises.mapNotNull { member -> exercise(member.exercise.id) }

    fun update(exerciseId: String, transform: (SessionExercise) -> SessionExercise): WorkoutSession = copy(exercises = exercises.map { exercise -> if (exercise.id == exerciseId) transform(exercise) else exercise })

    fun updateRow(exerciseId: String, index: Int, transform: (SessionRow) -> SessionRow): WorkoutSession = update(exerciseId) { exercise -> exercise.updateRow(index, transform) }

    fun withAddedSet(exerciseId: String, ghostFor: (exerciseId: String, setIndex: Int) -> LoggedSet?): WorkoutSession = update(exerciseId) { exercise -> exercise.withAddedSet(ghostFor(exerciseId, exercise.rows.size)) }

    /** "+ Add round" on a superset: one more set for the group's longest members. */
    fun withAddedRound(exerciseIds: List<String>, ghostFor: (exerciseId: String, setIndex: Int) -> LoggedSet?): WorkoutSession {
        val group = exercises.filter { exercise -> exercise.id in exerciseIds }
        val longest = group.maxOfOrNull { exercise -> exercise.rows.size } ?: return this
        return group.filter { exercise -> exercise.rows.size == longest }
            .fold(this) { session, exercise -> session.withAddedSet(exercise.id, ghostFor) }
    }

    /** First page with work left once something is logged (resume), else the first page. */
    fun initialPage(blocks: List<WorkoutBlock>): Int {
        if (exercises.none { exercise -> exercise.loggedCount > 0 }) return 0
        val firstOpen = blocks.indexOfFirst { block -> !exercisesOf(block).isBlockDone() }
        return firstOpen.takeIf { it >= 0 } ?: blocks.lastIndex.coerceAtLeast(0)
    }

    companion object {
        /** A session for [template]: the prescribed rows, last time's sets as ghosts, and [resumed]'s logged sets. */
        fun start(template: WorkoutTemplate, ghost: GhostSession?, resumed: InProgressSession?): WorkoutSession = WorkoutSession(
            template.exercises.map { templateExercise ->
                val exerciseId = templateExercise.exercise.id
                val ghostSets = ghost?.setsByExercise?.get(exerciseId).orEmpty()
                val persisted = resumed?.setsByExercise?.get(exerciseId).orEmpty()
                val highestLogged = persisted.maxOfOrNull { saved -> saved.set.setIndex } ?: -1
                val isCardio = templateExercise.exercise.trackingType == TrackingType.DURATION
                SessionExercise(
                    definition = templateExercise,
                    suggestion = ProgressiveOverloadRules.suggest(templateExercise, ghostSets),
                    rows = List(maxOf(templateExercise.targetSets, highestLogged + 1)) { index ->
                        val saved = persisted.firstOrNull { it.set.setIndex == index }
                        SessionRow(
                            index = index,
                            weightInput = if (isCardio) "" else saved?.set?.weightKg?.formatKg().orEmpty(),
                            amountInput = saved?.set
                                ?.let { set -> if (isCardio) set.durationMinutes else set.reps }
                                ?.toString().orEmpty(),
                            ghost = ghostSets.firstOrNull { it.setIndex == index },
                            persistedId = saved?.id,
                            persisted = saved?.set,
                            xp = saved?.xpAwarded,
                        )
                    },
                )
            },
        )
    }
}
