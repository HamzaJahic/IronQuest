package com.ironquest.app.domain.importing

import com.ironquest.app.domain.repository.ProgramProblem

/**
 * One thing wrong with an imported file, as data. The wording lives in `ui/imports/ImportCopy`
 * (string resources), so the parsers stay free of UI text. Values the message shows are fields.
 */
sealed interface ImportProblem {
    // CSV reader
    data class FileTooLarge(val maxKb: Int) : ImportProblem
    data object UnclosedQuote : ImportProblem
    data object EmptyFile : ImportProblem
    data object HeaderMissing : ImportProblem
    data class DuplicateColumn(val column: String) : ImportProblem
    data object NoDataRows : ImportProblem
    data class TooManyRows(val count: Int, val max: Int) : ImportProblem

    /** [missing] columns not in the header; [found] the header's columns (first 10, each cut to 30 characters; empty = none). */
    data class MissingColumns(val missing: List<String>, val found: List<String>) : ImportProblem
    data object MissingRepColumns : ImportProblem

    // Workout rows
    data object DayEmpty : ImportProblem
    data class DayNameTooLong(val day: String, val max: Int) : ImportProblem
    data class DuplicateExercise(val name: String, val day: String?, val firstLine: Int) : ImportProblem
    data class TooManyDays(val count: Int, val max: Int) : ImportProblem
    data class WrongDayCount(val expected: Int, val found: Int, val days: List<String>) : ImportProblem
    data class DayTooLong(val day: String, val count: Int, val max: Int) : ImportProblem
    data class WrongExerciseCount(val expected: Int, val found: Int) : ImportProblem
    data class SupersetNotConsecutive(val label: String, val line: Int, val previousLine: Int) : ImportProblem
    data class SupersetTooSmall(val label: String) : ImportProblem
    data object ExerciseNameEmpty : ImportProblem
    data class ExerciseNameTooLong(val max: Int) : ImportProblem
    data class BadType(val text: String) : ImportProblem
    data class BadStat(val text: String) : ImportProblem
    data class BadSets(val text: String, val max: Int) : ImportProblem
    data class BadRepNumbers(val min: String, val max: String) : ImportProblem
    data class BadRepRange(val min: Int, val max: Int, val limit: Int) : ImportProblem
    data class BadIncrement(val text: String, val max: Int) : ImportProblem
    data class SupersetLabelTooLong(val max: Int) : ImportProblem

    // Meal rows
    data class WrongMealCount(val expected: Int, val found: Int) : ImportProblem
    data class TooManyMeals(val count: Int, val max: Int) : ImportProblem
    data object SlotEmpty : ImportProblem
    data class SlotTooLong(val max: Int) : ImportProblem
    data class BadTime(val text: String) : ImportProblem
    data object MealNameEmpty : ImportProblem
    data class MealNameTooLong(val max: Int) : ImportProblem
    data class GramsEmpty(val column: String) : ImportProblem
    data class BadGrams(val column: String, val text: String, val max: Int) : ImportProblem
    data class BadKcal(val text: String, val max: Int) : ImportProblem
    data class BadUrl(val text: String) : ImportProblem

    // Raised outside the parsers (import screen)
    /** The parser crashed on the file; [errorType] is the exception's class name. */
    data class CouldntRead(val errorType: String) : ImportProblem
    data class ImportFailed(val errorType: String) : ImportProblem

    /** The picked file couldn't be opened at all. */
    data object CantOpenFile : ImportProblem

    /** A binary file or a spreadsheet instead of CSV text. */
    data object NotTextFile : ImportProblem
    data object NoPermission : ImportProblem

    /** A program change was refused. */
    data class ProgramRefused(val problem: ProgramProblem) : ImportProblem

    /** A message from the system or the file reader, shown as is. */
    data class Other(val text: String) : ImportProblem
}
