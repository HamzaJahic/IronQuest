package com.ironquest.app.domain.importing

import com.ironquest.app.domain.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvImportTest {

    private fun <T> ok(parse: ImportParse<T>): T = (parse as? ImportParse.Ok)?.value ?: error("Expected Ok, got $parse")
    private fun issues(parse: ImportParse<*>): List<ImportIssue> = (parse as? ImportParse.Failed)?.issues ?: error("Expected Failed, got $parse")

    @Test
    fun `every example in the AI prompts parses for its own kind`() {
        assertEquals(3, ok(WorkoutCsv.parse(ImportKind.PROGRAM.example, requireDay = true)).size)
        assertEquals(1, ok(WorkoutCsv.parse(ImportKind.WORKOUT_DAY.example, requireDay = true, expectedDays = 1)).size)
        assertEquals(1, ok(WorkoutCsv.parse(ImportKind.EXERCISE.example, requireDay = false, expectedExercises = 1)).single().exercises.size)
        assertEquals(3, ok(WorkoutCsv.parse(ImportKind.EXERCISES.example, requireDay = false)).single().exercises.size)
        assertEquals(1, ok(WorkoutCsv.parse(ImportKind.WORKOUT_DAYS.example, requireDay = true)).count { day -> day.exercises.any { it.supersetGroup != null } })
        assertEquals(1, ok(MealCsv.parseMeals(ImportKind.MEAL.example, expected = 1)).size)
        assertEquals(3, ok(MealCsv.parseMeals(ImportKind.MEALS.example)).size)
        assertEquals(4, ok(MealCsv.parsePlan(ImportKind.MEAL_PLAN.example)).size)
    }

    @Test
    fun `workout rows resolve built-in exercises and cardio`() {
        val days = ok(WorkoutCsv.parse(ImportKind.PROGRAM.example, requireDay = true))
        assertEquals("bench_press", days[0].exercises[0].definition.id)
        // "Cycling" matches the built-in by id; "Leg Curl" is not the built-in "Lying Leg Curl".
        val cycling = days[2].exercises.last()
        assertEquals("cycling", cycling.definition.id)
        assertEquals(TrackingType.DURATION, cycling.definition.trackingType)
        assertTrue(days[2].exercises[1].definition.id.startsWith(WorkoutCsv.CUSTOM_EXERCISE_PREFIX))
    }

    @Test
    fun `quoted commas, semicolons, BOM and code fences are tolerated`() {
        val text = "\uFEFF```csv\nMeal;Protein (g);Carbs;Fat\n\"Rice, chicken\";40;\"55,5\";10\n```"
        val meal = ok(MealCsv.parseMeals(text)).single()
        assertEquals("Rice, chicken", meal.name)
        assertEquals(55.5, meal.macros.carbsG, 1e-9)
    }

    @Test
    fun `errors carry line numbers and the copy text includes them and the format`() {
        val text = "day,exercise,sets,rep_min,rep_max\nPush,Bench,4,5,8\nPush,Fly,x,12,8\nPush,Bench,3,5,8"
        val found = issues(WorkoutCsv.parse(text, requireDay = true))
        // Line 3: bad sets + bad range; line 4: Bench repeated.
        assertEquals(listOf(3, 3, 4), found.map { it.line })
        assertEquals(
            listOf<ImportProblem>(ImportProblem.BadSets("x", 20), ImportProblem.BadRepRange(12, 8, 100), ImportProblem.DuplicateExercise("Bench", "Push", 2)),
            found.map { it.problem },
        )
    }

    @Test
    fun `duplicate exercise in a day and missing columns are reported`() {
        val dup = "day,exercise,sets,rep_min,rep_max\nPush,Bench,4,5,8\nPush,bench,3,5,8"
        assertEquals(3, issues(WorkoutCsv.parse(dup, requireDay = true)).single().line)
        val missing = issues(MealCsv.parsePlan("meal,protein_g\nOats,30"))
        assertEquals(ImportProblem.MissingColumns(listOf("slot", "carbs_g", "fat_g"), listOf("meal", "protein_g")), missing.single().problem)
        assertEquals(ImportProblem.EmptyFile, issues(MealCsv.parseMeals("")).single().problem)
        assertEquals(ImportProblem.UnclosedQuote, issues(MealCsv.parseMeals("meal,protein_g,carbs_g,fat_g\n\"broken,1,2,3")).single().problem)
    }

    @Test
    fun `a superset label groups consecutive rows of a day`() {
        val text = "day,exercise,sets,rep_min,rep_max,superset\nPush,Bench,4,5,8,\nPush,Incline press,3,8,12,A\nPush,Cable row,2,8,12,a\nPull,Row,3,6,10,A\nPull,Curl,3,8,12,A"
        val days = ok(WorkoutCsv.parse(text, requireDay = true))
        assertEquals(listOf(null, "a", "a"), days[0].exercises.map { it.supersetGroup })
        // Labels are per day: Pull's "A" is its own group.
        assertEquals(listOf("a", "a"), days[1].exercises.map { it.supersetGroup })
        // The "group" alias works too, and the examples show the pair.
        val alias = ok(WorkoutCsv.parse("exercise,sets,rep_min,rep_max,group\nA,3,8,12,x\nB,3,8,12,x", requireDay = false))
        assertEquals(listOf("x", "x"), alias.single().exercises.map { it.supersetGroup })
        assertTrue(ok(WorkoutCsv.parse(ImportKind.EXERCISES.example, requireDay = false)).single().exercises.take(2).all { it.supersetGroup == "a" })
    }

    @Test
    fun `a superset must be consecutive and have at least two exercises`() {
        val split = "day,exercise,sets,rep_min,rep_max,superset\nPush,Bench,4,5,8,B\nPush,Fly,3,8,12,B\nPush,Dips,3,8,12,\nPush,Pushdown,3,10,15,B"
        val splitIssue = issues(WorkoutCsv.parse(split, requireDay = true)).single()
        assertEquals(5, splitIssue.line)
        assertEquals(ImportProblem.SupersetNotConsecutive("B", 5, 3), splitIssue.problem)

        val single = "day,exercise,sets,rep_min,rep_max,superset\nPush,Bench,4,5,8,\nPush,Fly,3,8,12,C"
        val singleIssue = issues(WorkoutCsv.parse(single, requireDay = true)).single()
        assertEquals(3, singleIssue.line)
        assertEquals(ImportProblem.SupersetTooSmall("C"), singleIssue.problem)
    }
}
