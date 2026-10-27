package com.ironquest.app.ui.imports

import com.ironquest.app.R
import com.ironquest.app.domain.repository.ProgramProblem
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiText

/** The words for a [ProgramProblem]. Pure, so it runs in JVM tests. */
object ProgramCopy {
    fun problem(problem: ProgramProblem): UiText = when (problem) {
        ProgramProblem.ProgramGone -> uiText(R.string.program_problem_program_gone)
        ProgramProblem.DayGone -> uiText(R.string.program_problem_day_gone)
        ProgramProblem.BuiltInReadOnly -> uiText(R.string.program_problem_built_in_read_only)
        is ProgramProblem.AlreadyInDay ->
            uiText(R.string.program_problem_already_in_day, joinedText(problem.exercises.map(::exerciseNameText), R.string.common_list_separator))
        ProgramProblem.RestoreBuiltInFirst -> uiText(R.string.program_problem_restore_built_in_first)
        ProgramProblem.WorkoutInProgress -> uiText(R.string.program_problem_workout_in_progress)
    }
}
