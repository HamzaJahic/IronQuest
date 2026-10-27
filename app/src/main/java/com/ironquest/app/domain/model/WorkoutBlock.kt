package com.ironquest.app.domain.model

/** One page of the workout screen: a single exercise, or a superset done in alternating rounds. */
sealed interface WorkoutBlock {
    val exercises: List<TemplateExercise>

    data class Single(val exercise: TemplateExercise) : WorkoutBlock {
        override val exercises: List<TemplateExercise> get() = listOf(exercise)
    }

    /** [letter] is derived from position ("A" = the day's first superset), never stored. */
    data class Superset(val letter: String, override val exercises: List<TemplateExercise>) : WorkoutBlock

    companion object {
        /**
         * Splits a day into blocks: a run of consecutive exercises with the same non-null
         * [TemplateExercise.supersetGroup] is one superset. A run of one (shouldn't happen after
         * import validation) stays a single exercise.
         */
        fun of(exercises: List<TemplateExercise>): List<WorkoutBlock> {
            val runs = mutableListOf<MutableList<TemplateExercise>>()
            for (exercise in exercises) {
                val last = runs.lastOrNull()
                if (exercise.supersetGroup != null && last != null && last.first().supersetGroup == exercise.supersetGroup) {
                    last += exercise
                } else {
                    runs += mutableListOf(exercise)
                }
            }
            var letters = 0
            return runs.map { run ->
                if (run.size == 1) Single(run.single()) else Superset(('A' + letters++).toString(), run)
            }
        }
    }
}

/**
 * Order of sets in a superset: round 1 is set 1 of every member in order, round 2 set 2, …
 * Rounds = the largest set count; a member with fewer sets is skipped in later rounds.
 */
object SupersetRounds {

    /** Set [setIndex] (0-based, = round − 1) of member [member] (0-based position in the group). */
    data class Step(val member: Int, val setIndex: Int)

    fun rounds(setCounts: List<Int>): Int = setCounts.maxOrNull() ?: 0

    fun order(setCounts: List<Int>): List<Step> = (0 until rounds(setCounts)).flatMap { round ->
        setCounts.indices.filter { round < setCounts[it] }.map { Step(it, round) }
    }

    /** The first step not logged yet, or null when every round is done. */
    fun next(setCounts: List<Int>, isLogged: (Step) -> Boolean): Step? = order(setCounts).firstOrNull { !isLogged(it) }

    /** The step that follows [step] once it's logged (the next unlogged one after it), or null. */
    fun after(setCounts: List<Int>, step: Step, isLogged: (Step) -> Boolean): Step? = order(setCounts).dropWhile { it != step }.drop(1).firstOrNull { !isLogged(it) }
}
