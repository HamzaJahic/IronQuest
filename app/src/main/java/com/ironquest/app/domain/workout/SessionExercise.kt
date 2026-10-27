package com.ironquest.app.domain.workout

import com.ironquest.app.domain.goals.WorkoutGoal
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.progression.OverloadSuggestion
import com.ironquest.app.domain.progression.XpRules
import com.ironquest.app.domain.progression.formatKg

/** One exercise of a live workout with its set rows. All changes return a new copy. */
data class SessionExercise(
    val definition: TemplateExercise,
    val suggestion: OverloadSuggestion,
    val rows: List<SessionRow>,
    /** Today's goal for this exercise, only when its type is shown. */
    val goal: WorkoutGoal? = null,
    val goalReached: Boolean = false,
) {
    val id: String get() = definition.exercise.id
    val isCardio: Boolean get() = definition.exercise.trackingType == TrackingType.DURATION
    val loggedCount: Int get() = rows.count { row -> row.isLogged }
    val isDone: Boolean get() = rows.all { row -> row.isLogged }
    val loggedSets: List<LoggedSet> get() = rows.mapNotNull { row -> row.persisted }

    /** The set the steppers edit: the first one not logged yet, or null when all are. */
    val currentRow: SessionRow? get() = rows.firstOrNull { row -> !row.isLogged }

    /** Weight stepper step: the overload increment, [SetInput.FALLBACK_STEP_KG] when there is none. */
    val weightStepKg: Double get() = definition.exercise.incrementKg.takeIf { it > 0.0 } ?: SetInput.FALLBACK_STEP_KG

    /** What the big weight number shows: the typed weight, else today's suggestion, else last time's; "" the first time. */
    fun shownWeight(row: SessionRow): String {
        if (isCardio) return ""
        if (row.weightInput.isNotBlank()) return row.weightInput
        val fallback = suggestion.suggestedWeightKg ?: row.ghost?.weightKg
        return fallback?.formatKg().orEmpty()
    }

    /** Where an empty reps/minutes stepper starts: last time's value for the set, else the range bottom. */
    fun startAmount(row: SessionRow): Int {
        val lastTime = row.ghost?.let { ghost -> if (isCardio) ghost.durationMinutes else ghost.reps }
        return lastTime?.takeIf { it > 0 } ?: definition.repRange.min
    }

    /** The row's inputs as a set, or null when they aren't a valid set yet. */
    fun parse(row: SessionRow): LoggedSet? {
        val amount = row.amountInput.toIntOrNull()?.takeIf { it > 0 } ?: return null
        if (isCardio) return LoggedSet(setIndex = row.index, reps = 0, weightKg = 0.0, durationMinutes = amount)
        val weight = row.weightInput.toDoubleOrNull()?.takeIf { it in 0.0..SetInput.MAX_WEIGHT_KG } ?: return null
        return LoggedSet(setIndex = row.index, reps = amount, weightKg = weight)
    }

    fun statusOf(row: SessionRow): RowStatus {
        val parsed = parse(row)
        return when {
            row.isLogged && parsed != null && parsed != row.persisted -> RowStatus.DIRTY
            row.isLogged -> RowStatus.LOGGED
            parsed != null -> RowStatus.READY
            else -> RowStatus.EMPTY
        }
    }

    /** Live "beats last time" preview while the user types, before the set is logged. */
    fun beatsGhost(row: SessionRow): Boolean {
        val ghost = row.ghost ?: return false
        val parsed = parse(row) ?: return false
        return XpRules.beatsGhost(parsed, ghost, definition.exercise.trackingType)
    }

    fun updateRow(index: Int, transform: (SessionRow) -> SessionRow): SessionExercise = copy(rows = rows.map { row -> if (row.index == index) transform(row) else row })

    /** One more row after the last, starting from the last row's inputs (weight only for cardio). */
    fun withAddedSet(ghost: LoggedSet?): SessionExercise {
        val last = rows.lastOrNull()
        return copy(
            rows = rows + SessionRow(
                index = rows.size,
                weightInput = last?.weightInput.orEmpty(),
                amountInput = if (isCardio) "" else last?.amountInput.orEmpty(),
                ghost = ghost,
            ),
        )
    }

    /**
     * [set] was stored as [setId] and paid [xp]. Strength work carries its weight and reps into the
     * open rows below it, overwriting earlier carries, so the next set starts from this one.
     */
    fun withLoggedSet(set: LoggedSet, setId: Long, xp: Int): SessionExercise = copy(
        rows = rows.map { row ->
            when {
                row.index == set.setIndex -> row.copy(persistedId = setId, persisted = set, xp = xp, isSaving = false)
                !isCardio && !row.isLogged && row.index > set.setIndex ->
                    row.copy(weightInput = set.weightKg.formatKg(), amountInput = set.reps.toString())
                else -> row
            }
        },
    )

    /** The set at [index] was deleted: an extra last set disappears, any other row opens again with its values. */
    fun withoutSet(index: Int): SessionExercise {
        val isExtraLast = index >= definition.targetSets && index == rows.lastIndex
        if (isExtraLast) return copy(rows = rows.dropLast(1))
        return updateRow(index) { row -> row.copy(persistedId = null, persisted = null, xp = null, isSaving = false) }
    }

    /** −/+ on the weight, starting from the shown weight. */
    fun withSteppedWeight(index: Int, up: Boolean): SessionExercise {
        val row = rows.getOrNull(index) ?: return this
        val next = SetInput.stepWeight(shownWeight(row), weightStepKg, up)
        return updateRow(index) { it.copy(weightInput = next) }
    }

    /** −/+ on reps (minutes for cardio). */
    fun withSteppedAmount(index: Int, up: Boolean): SessionExercise {
        val row = rows.getOrNull(index) ?: return this
        val next = SetInput.stepAmount(row.amountInput, up, startAmount(row))
        return updateRow(index) { it.copy(amountInput = next) }
    }
}
