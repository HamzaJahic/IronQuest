package com.ironquest.app.domain.repository

import com.ironquest.app.domain.model.ExerciseDefinition

/**
 * Why a program change was refused, as data. The wording lives in `ui/imports/ProgramCopy`
 * (string resources), so the repository stays free of UI text.
 */
sealed interface ProgramProblem {
    /** The program was deleted meanwhile. */
    data object ProgramGone : ProgramProblem

    /** The workout day was deleted meanwhile. */
    data object DayGone : ProgramProblem

    /** Built-in workout days can't take new exercises. */
    data object BuiltInReadOnly : ProgramProblem

    /** The day already has these exercises. */
    data class AlreadyInDay(val exercises: List<ExerciseDefinition>) : ProgramProblem

    /** The built-in program is deleted, so it can't be made active. */
    data object RestoreBuiltInFirst : ProgramProblem

    /** One of the program's days has an unfinished workout. */
    data object WorkoutInProgress : ProgramProblem
}

/** A refused program change. [message] is for developers and logs only; the UI shows [problem]. */
class ProgramException(val problem: ProgramProblem) : IllegalStateException(problem.toString())
