package com.ironquest.app.domain.importing

enum class ImportFamily { WORKOUT, MEAL }

/**
 * What a CSV import creates. Kinds of one [family] share a CSV format; "one" vs "several"
 * only changes the AI prompt wording and the row/day count check. Labels, subjects, rules and
 * the AI prompt are UI text: see `ui/imports/ImportCopy`.
 */
enum class ImportKind(val family: ImportFamily) {
    PROGRAM(ImportFamily.WORKOUT),
    WORKOUT_DAY(ImportFamily.WORKOUT),
    WORKOUT_DAYS(ImportFamily.WORKOUT),
    EXERCISE(ImportFamily.WORKOUT),
    EXERCISES(ImportFamily.WORKOUT),
    MEAL(ImportFamily.MEAL),
    MEALS(ImportFamily.MEAL),
    MEAL_PLAN(ImportFamily.MEAL),
    ;

    val siblings: List<ImportKind> get() = entries.filter { it.family == family }

    /** True for the kinds whose rules describe exercises without a day column. */
    val isExerciseList: Boolean get() = this == EXERCISE || this == EXERCISES

    val example: String
        get() = when (this) {
            PROGRAM -> WorkoutCsv.EXAMPLE_PROGRAM
            WORKOUT_DAY -> WorkoutCsv.EXAMPLE_DAY
            WORKOUT_DAYS -> WorkoutCsv.EXAMPLE_PROGRAM
            EXERCISE -> WorkoutCsv.EXAMPLE_EXERCISE
            EXERCISES -> WorkoutCsv.EXAMPLE_EXERCISES
            MEAL -> MealCsv.EXAMPLE_MEAL
            MEALS -> MealCsv.EXAMPLE_MEALS
            MEAL_PLAN -> MealCsv.EXAMPLE_PLAN
        }
}
