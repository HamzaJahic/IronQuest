package com.ironquest.app.domain.goals

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.nutrition.DayNutrition
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.progression.OverloadSuggestion
import com.ironquest.app.domain.progression.ProgressiveOverloadRules

/** Pure goal rules: which goals today's workout and food offer, and when they're done. */
object GoalRules {

    /**
     * Workout goals for [template] given the last completed session's sets per exercise
     * ([lastSetsByExercise], usually the ghost). In template order; at most one goal per exercise.
     */
    fun forWorkout(template: WorkoutTemplate, lastSetsByExercise: Map<String, List<LoggedSet>>): List<WorkoutGoal> = template.exercises.mapNotNull { te ->
        val last = lastSetsByExercise[te.exercise.id].orEmpty().sortedBy { it.setIndex }
        if (last.isEmpty()) return@mapNotNull null
        when (val suggestion = ProgressiveOverloadRules.suggest(te, last)) {
            is OverloadSuggestion.LevelUp -> LimitBreakGoal(te, last, suggestion.previousWeightKg, suggestion.suggestedWeightKg)
            else -> BeatLastTimeGoal(te, last, volume(last, te.exercise.trackingType))
        }
    }

    /**
     * Bonuses for finishing a session of [template]: goals from [lastSetsByExercise] (the previous
     * completed session), judged on the final [sessionSetsByExercise], paid only for [enabled] types.
     */
    fun completionAward(
        template: WorkoutTemplate,
        lastSetsByExercise: Map<String, List<LoggedSet>>,
        sessionSetsByExercise: Map<String, List<LoggedSet>>,
        enabled: Set<GoalType>,
    ): GoalAward {
        val reached = reached(forWorkout(template, lastSetsByExercise), sessionSetsByExercise, enabled)
        val statOf = template.exercises.associate { it.exercise.id to it.exercise.stat }
        val statXp = reached.groupBy { statOf.getValue(it.exerciseId) }.mapValues { (_, list) -> list.sumOf { it.xp } }
        return GoalAward(reached, statXp)
    }

    /** Protein and water goals of today's active plan, in that order; none without targets. */
    fun forFood(day: DayNutrition): List<FoodGoal> {
        val targets = day.targets ?: return emptyList()
        val protein = day.logs.fold(Macros.ZERO) { acc, log -> acc + log.eaten }.proteinG
        return listOfNotNull(
            targets.proteinG?.takeIf { it > 0 }?.let { FoodGoal(FoodTarget.PROTEIN, protein, it) },
            targets.waterMl?.takeIf { it > 0 }?.let { FoodGoal(FoodTarget.WATER, day.waterMl.toDouble(), it.toDouble()) },
        )
    }

    /**
     * Done from this session's sets of the goal's exercise. Limit break: every prescribed set
     * logged at ≥ the target weight. Beat last time: more volume than last time.
     */
    fun isDone(goal: WorkoutGoal, sessionSets: List<LoggedSet>): Boolean = when (goal) {
        is LimitBreakGoal ->
            sessionSets.count { it.weightKg >= goal.targetWeightKg - EPS } >= goal.exercise.targetSets
        is BeatLastTimeGoal ->
            volume(sessionSets, goal.exercise.exercise.trackingType) > goal.lastVolume + EPS
    }

    /**
     * Exercise volume: per set `weightKg × reps` when weight > 0, otherwise `reps`
     * (bodyweight); cardio counts minutes. Summed over [sets].
     */
    fun volume(sets: List<LoggedSet>, trackingType: TrackingType): Double = sets.sumOf { set ->
        when (trackingType) {
            TrackingType.DURATION -> (set.durationMinutes ?: 0).toDouble()
            TrackingType.WEIGHT_REPS -> if (set.weightKg > 0.0) set.weightKg * set.reps else set.reps.toDouble()
        }
    }

    /** Goals reached in a finished session, for the goal types in [enabled]. */
    fun reached(
        goals: List<WorkoutGoal>,
        sessionSetsByExercise: Map<String, List<LoggedSet>>,
        enabled: Set<GoalType>,
    ): List<GoalReached> = goals
        .filter { it.type in enabled && isDone(it, sessionSetsByExercise[it.exercise.exercise.id].orEmpty()) }
        .map { GoalReached(it.type, it.exercise.exercise.id, it.exercise.exercise.name, it.bonusXp) }

    private const val EPS = 1e-9
}
