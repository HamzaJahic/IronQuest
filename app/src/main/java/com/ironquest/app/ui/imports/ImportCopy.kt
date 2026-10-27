package com.ironquest.app.ui.imports

import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportIssue
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.importing.ImportProblem
import com.ironquest.app.domain.importing.MealCsv
import com.ironquest.app.domain.importing.WorkoutCsv
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiText

/**
 * The words of the import screens as [UiText]: kind chips, the AI prompt, the error report and one
 * sentence per [ImportProblem]. Pure, so it runs in JVM tests. CSV column names and the example
 * files stay English in the domain: the parsers read them.
 */
object ImportCopy {

    /** Chip label. */
    fun label(kind: ImportKind): UiText = uiText(
        when (kind) {
            ImportKind.PROGRAM -> R.string.import_kind_program_label
            ImportKind.WORKOUT_DAY -> R.string.import_kind_workout_day_label
            ImportKind.WORKOUT_DAYS -> R.string.import_kind_workout_days_label
            ImportKind.EXERCISE -> R.string.import_kind_exercise_label
            ImportKind.EXERCISES -> R.string.import_kind_exercises_label
            ImportKind.MEAL -> R.string.import_kind_meal_label
            ImportKind.MEALS -> R.string.import_kind_meals_label
            ImportKind.MEAL_PLAN -> R.string.import_kind_meal_plan_label
        },
    )

    /** Used in the AI prompt: "Take the <subject> we discussed…". */
    fun subject(kind: ImportKind): UiText = uiText(
        when (kind) {
            ImportKind.PROGRAM -> R.string.import_kind_program_subject
            ImportKind.WORKOUT_DAY -> R.string.import_kind_workout_day_subject
            ImportKind.WORKOUT_DAYS -> R.string.import_kind_workout_days_subject
            ImportKind.EXERCISE -> R.string.import_kind_exercise_subject
            ImportKind.EXERCISES -> R.string.import_kind_exercises_subject
            ImportKind.MEAL -> R.string.import_kind_meal_subject
            ImportKind.MEALS -> R.string.import_kind_meals_subject
            ImportKind.MEAL_PLAN -> R.string.import_kind_meal_plan_subject
        },
    )

    /** The subject with its article ("an exercise", "a meal plan"), for sentences like "as <a/an …>". */
    fun subjectWithArticle(kind: ImportKind): UiText = uiText(
        when (kind) {
            ImportKind.PROGRAM -> R.string.import_kind_program_subject_indefinite
            ImportKind.WORKOUT_DAY -> R.string.import_kind_workout_day_subject_indefinite
            ImportKind.WORKOUT_DAYS -> R.string.import_kind_workout_days_subject_indefinite
            ImportKind.EXERCISE -> R.string.import_kind_exercise_subject_indefinite
            ImportKind.EXERCISES -> R.string.import_kind_exercises_subject_indefinite
            ImportKind.MEAL -> R.string.import_kind_meal_subject_indefinite
            ImportKind.MEALS -> R.string.import_kind_meals_subject_indefinite
            ImportKind.MEAL_PLAN -> R.string.import_kind_meal_plan_subject_indefinite
        },
    )

    /** Page title: one whole string per kind, because the article ("a" / "an") can't be built from the subject in other languages. */
    fun title(kind: ImportKind): UiText = uiText(
        when (kind) {
            ImportKind.PROGRAM -> R.string.import_title_program
            ImportKind.WORKOUT_DAY -> R.string.import_title_workout_day
            ImportKind.WORKOUT_DAYS -> R.string.import_title_workout_days
            ImportKind.EXERCISE -> R.string.import_title_exercise
            ImportKind.EXERCISES -> R.string.import_title_exercises
            ImportKind.MEAL -> R.string.import_title_meal
            ImportKind.MEALS -> R.string.import_title_meals
            ImportKind.MEAL_PLAN -> R.string.import_title_meal_plan
        },
    )

    /** Second step of "Get the CSV". */
    fun pasteStep(kind: ImportKind): UiText = uiText(R.string.import_step_paste, subject(kind))

    /** Snackbar after "Copy prompt". */
    fun promptCopied(kind: ImportKind): UiText = uiText(R.string.import_prompt_copied, subject(kind))

    fun rules(kind: ImportKind): UiText = when {
        kind.isExerciseList -> uiText(R.string.import_rules_exercises, WorkoutCsv.MAX_SETS)
        kind == ImportKind.PROGRAM || kind == ImportKind.WORKOUT_DAY || kind == ImportKind.WORKOUT_DAYS ->
            uiText(R.string.import_rules_program, WorkoutCsv.MAX_NAME, WorkoutCsv.MAX_SETS)
        kind == ImportKind.MEAL_PLAN -> uiText(R.string.import_rules_meal_plan, MealCsv.MAX_SLOTS)
        else -> uiText(R.string.import_rules_meals, MealCsv.MAX_NAME)
    }

    /**
     * Copied by "Copy prompt". The user pastes it into the AI chat where they already worked out
     * the plan, so it refers to "the <subject> we discussed" and carries the format, the rules and
     * how to answer.
     */
    fun aiPrompt(kind: ImportKind): UiText = uiText(R.string.import_ai_prompt, subject(kind), kind.example, rules(kind))

    /** Copied by "Copy error": everything an AI needs to fix the file it produced. */
    fun errorReport(kind: ImportKind, issues: List<ImportIssue>): UiText = uiText(
        R.string.import_error_report,
        subjectWithArticle(kind),
        joinedText(issues.map { uiText(R.string.import_error_report_item, issue(it)) }, R.string.import_error_report_item_separator),
        kind.example,
        rules(kind),
    )

    /** One problem as the user reads it: "Line 3: sets "x" must be…" or, for the whole file, just the sentence. */
    fun issue(issue: ImportIssue): UiText {
        val text = problem(issue.problem)
        return if (issue.line != null) uiText(R.string.import_issue_with_line, issue.line, text) else text
    }

    fun problem(problem: ImportProblem): UiText = when (problem) {
        is ImportProblem.FileTooLarge -> uiText(R.string.import_problem_file_too_large, problem.maxKb)
        ImportProblem.UnclosedQuote -> uiText(R.string.import_problem_unclosed_quote)
        ImportProblem.EmptyFile -> uiText(R.string.import_problem_empty_file)
        ImportProblem.HeaderMissing -> uiText(R.string.import_problem_header_missing)
        is ImportProblem.DuplicateColumn -> uiText(R.string.import_problem_duplicate_column, problem.column)
        ImportProblem.NoDataRows -> uiText(R.string.import_problem_no_data_rows)
        is ImportProblem.TooManyRows -> uiText(R.string.import_problem_too_many_rows, problem.count, problem.max)
        is ImportProblem.MissingColumns -> uiText(
            R.string.import_problem_missing_columns,
            joinedText(problem.missing.map(UiText::Verbatim), R.string.common_list_separator),
            if (problem.found.isEmpty()) {
                uiText(R.string.import_problem_missing_columns_none)
            } else {
                joinedText(problem.found.map(UiText::Verbatim), R.string.common_list_separator)
            },
        )
        ImportProblem.MissingRepColumns -> uiText(R.string.import_problem_missing_rep_columns)
        ImportProblem.DayEmpty -> uiText(R.string.import_problem_day_empty)
        is ImportProblem.DayNameTooLong -> uiText(R.string.import_problem_day_name_too_long, problem.day, problem.max)
        is ImportProblem.DuplicateExercise ->
            if (problem.day != null) {
                uiText(R.string.import_problem_duplicate_exercise_in_day, problem.name, problem.day, problem.firstLine)
            } else {
                uiText(R.string.import_problem_duplicate_exercise, problem.name, problem.firstLine)
            }
        is ImportProblem.TooManyDays -> uiText(R.string.import_problem_too_many_days, problem.count, problem.max)
        is ImportProblem.WrongDayCount -> uiText(
            R.string.import_problem_wrong_day_count,
            problem.expected,
            problem.found,
            joinedText(problem.days.map(UiText::Verbatim), R.string.common_list_separator),
        )
        is ImportProblem.DayTooLong -> uiText(R.string.import_problem_day_too_long, problem.day, problem.count, problem.max)
        is ImportProblem.WrongExerciseCount -> uiText(R.string.import_problem_wrong_exercise_count, problem.expected, problem.found)
        is ImportProblem.SupersetNotConsecutive ->
            uiText(R.string.import_problem_superset_not_consecutive, problem.label, problem.line, problem.previousLine)
        is ImportProblem.SupersetTooSmall -> uiText(R.string.import_problem_superset_too_small, problem.label)
        ImportProblem.ExerciseNameEmpty -> uiText(R.string.import_problem_exercise_name_empty)
        is ImportProblem.ExerciseNameTooLong -> uiText(R.string.import_problem_exercise_name_too_long, problem.max)
        is ImportProblem.BadType -> uiText(R.string.import_problem_bad_type, problem.text)
        is ImportProblem.BadStat -> uiText(R.string.import_problem_bad_stat, problem.text)
        is ImportProblem.BadSets -> uiText(R.string.import_problem_bad_sets, problem.text, problem.max)
        is ImportProblem.BadRepNumbers -> uiText(R.string.import_problem_bad_rep_numbers, problem.min, problem.max)
        is ImportProblem.BadRepRange -> uiText(R.string.import_problem_bad_rep_range, problem.min, problem.max, problem.limit)
        is ImportProblem.BadIncrement -> uiText(R.string.import_problem_bad_increment, problem.text, problem.max)
        is ImportProblem.SupersetLabelTooLong -> uiText(R.string.import_problem_superset_label_too_long, problem.max)
        is ImportProblem.WrongMealCount -> uiText(R.string.import_problem_wrong_meal_count, problem.expected, problem.found)
        is ImportProblem.TooManyMeals -> uiText(R.string.import_problem_too_many_meals, problem.count, problem.max)
        ImportProblem.SlotEmpty -> uiText(R.string.import_problem_slot_empty)
        is ImportProblem.SlotTooLong -> uiText(R.string.import_problem_slot_too_long, problem.max)
        is ImportProblem.BadTime -> uiText(R.string.import_problem_bad_time, problem.text)
        ImportProblem.MealNameEmpty -> uiText(R.string.import_problem_meal_name_empty)
        is ImportProblem.MealNameTooLong -> uiText(R.string.import_problem_meal_name_too_long, problem.max)
        is ImportProblem.GramsEmpty -> uiText(R.string.import_problem_grams_empty, problem.column)
        is ImportProblem.BadGrams -> uiText(R.string.import_problem_bad_grams, problem.column, problem.text, problem.max)
        is ImportProblem.BadKcal -> uiText(R.string.import_problem_bad_kcal, problem.text, problem.max)
        is ImportProblem.BadUrl -> uiText(R.string.import_problem_bad_url, problem.text)
        is ImportProblem.CouldntRead -> uiText(R.string.import_problem_couldnt_read, problem.errorType)
        is ImportProblem.ImportFailed -> uiText(R.string.import_problem_import_failed, problem.errorType)
        ImportProblem.CantOpenFile -> uiText(R.string.import_problem_cant_open_file)
        ImportProblem.NotTextFile -> uiText(R.string.import_problem_not_text_file)
        ImportProblem.NoPermission -> uiText(R.string.import_problem_no_permission)
        is ImportProblem.ProgramRefused -> ProgramCopy.problem(problem.problem)
        is ImportProblem.Other -> UiText.Verbatim(problem.text)
    }
}
