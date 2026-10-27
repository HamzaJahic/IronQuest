package com.ironquest.app.ui.imports

import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportIssue
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.importing.ImportProblem
import com.ironquest.app.domain.importing.MealCsv
import com.ironquest.app.domain.importing.WorkoutCsv
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.repository.ProgramProblem
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.catalog.programNameText
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImportCopyTest {

    private fun english(text: UiText) = EnglishText.resolve(text)
    private fun golden(name: String) = File("src/test/resources/import/$name.txt").readText()
    private fun say(problem: ImportProblem, line: Int? = null) = english(ImportCopy.issue(ImportIssue(line, problem)))

    @Test
    fun `labels and subjects keep their English wording`() {
        assertEquals(
            listOf("Whole plan", "One day", "Several days", "One exercise", "Several exercises", "One meal", "Several meals", "Meal plan"),
            ImportKind.entries.map { english(ImportCopy.label(it)) },
        )
        assertEquals(
            listOf("workout plan", "workout", "set of workouts", "exercise", "list of exercises", "meal", "list of meals", "meal plan"),
            ImportKind.entries.map { english(ImportCopy.subject(it)) },
        )
    }

    @Test
    fun `every AI prompt is word for word what it was before the strings moved to resources`() {
        ImportKind.entries.forEach { kind ->
            assertEquals(kind.name, golden("prompt_${kind.name}"), english(ImportCopy.aiPrompt(kind)))
        }
    }

    @Test
    fun `every error report is word for word what it was before`() {
        val issues = listOf(ImportIssue(3, ImportProblem.BadSets("x", 20)), ImportIssue(null, ImportProblem.EmptyFile))
        ImportKind.entries.forEach { kind ->
            assertEquals(kind.name, golden("report_${kind.name}"), english(ImportCopy.errorReport(kind, issues)))
        }
    }

    @Test
    fun `the report is built from the subject, the problems, the example and the rules`() {
        val issues = listOf(ImportIssue(5, ImportProblem.SupersetTooSmall("C")))
        val report = ImportCopy.errorReport(ImportKind.PROGRAM, issues)
        val args = (report as UiText.Res).args
        assertEquals(R.string.import_error_report, report.id)
        assertEquals(ImportCopy.subjectWithArticle(ImportKind.PROGRAM), args[0])
        assertEquals(ImportKind.PROGRAM.example, args[2])
        assertEquals(ImportCopy.rules(ImportKind.PROGRAM), args[3])
        assertTrue(english(report).contains("- Line 5: Superset C has only one exercise"))
        assertTrue(english(report).contains(WorkoutCsv.EXAMPLE_PROGRAM))
    }

    @Test
    fun `rules follow the kind`() {
        assertEquals(uiText(R.string.import_rules_exercises, WorkoutCsv.MAX_SETS), ImportCopy.rules(ImportKind.EXERCISES))
        assertEquals(uiText(R.string.import_rules_program, WorkoutCsv.MAX_NAME, WorkoutCsv.MAX_SETS), ImportCopy.rules(ImportKind.WORKOUT_DAY))
        assertEquals(uiText(R.string.import_rules_meals, MealCsv.MAX_NAME), ImportCopy.rules(ImportKind.MEAL))
        assertEquals(uiText(R.string.import_rules_meal_plan, MealCsv.MAX_SLOTS), ImportCopy.rules(ImportKind.MEAL_PLAN))
    }

    @Test
    fun `every parse problem keeps its English message`() {
        val expected = listOf(
            ImportProblem.FileTooLarge(512) to "The file is too large (max 512 KB).",
            ImportProblem.UnclosedQuote to "A quoted value is never closed (missing \").",
            ImportProblem.EmptyFile to "The file is empty.",
            ImportProblem.HeaderMissing to "The first line must be the header row.",
            ImportProblem.DuplicateColumn("sets") to "Column \"sets\" appears twice in the header.",
            ImportProblem.NoDataRows to "The file has a header but no data rows.",
            ImportProblem.TooManyRows(501, 500) to "Too many rows (501); the limit is 500.",
            ImportProblem.MissingColumns(listOf("day", "sets"), listOf("a", "b")) to "Missing column(s): day, sets. The header row has: a, b.",
            ImportProblem.MissingColumns(listOf("day"), emptyList()) to "Missing column(s): day. The header row has: none.",
            ImportProblem.MissingRepColumns to "Missing column(s): rep_min, rep_max.",
            ImportProblem.DayEmpty to "day is empty.",
            ImportProblem.DayNameTooLong("Long", 60) to "day \"Long\" is longer than 60 characters.",
            ImportProblem.DuplicateExercise("Bench", "Push", 2) to "\"Bench\" is already listed in day \"Push\" (line 2). Each exercise can appear only once per day.",
            ImportProblem.DuplicateExercise("Bench", null, 2) to "\"Bench\" is already listed (line 2). Each exercise can appear only once per day.",
            ImportProblem.TooManyDays(15, 14) to "Too many days (15); the limit is 14.",
            ImportProblem.WrongDayCount(1, 2, listOf("Push", "Pull")) to "Expected exactly 1 workout day but found 2: Push, Pull.",
            ImportProblem.DayTooLong("Push", 31, 30) to "Day \"Push\" has 31 exercises; the limit is 30.",
            ImportProblem.WrongExerciseCount(1, 3) to "Expected exactly 1 exercise but found 3.",
            ImportProblem.SupersetNotConsecutive("B", 5, 3) to "Superset B on line 5 must follow line 3 directly.",
            ImportProblem.SupersetTooSmall("C") to "Superset C has only one exercise; a superset needs at least 2, or leave superset empty.",
            ImportProblem.ExerciseNameEmpty to "exercise is empty.",
            ImportProblem.ExerciseNameTooLong(60) to "exercise name is longer than 60 characters.",
            ImportProblem.BadType("x") to "type \"x\" must be \"reps\" or \"minutes\".",
            ImportProblem.BadStat("x") to "stat \"x\" must be STR, CON or STA.",
            ImportProblem.BadSets("x", 20) to "sets \"x\" must be a whole number from 1 to 20.",
            ImportProblem.BadRepNumbers("a", "b") to "rep_min \"a\" and rep_max \"b\" must be whole numbers.",
            ImportProblem.BadRepRange(12, 8, 100) to "rep range 12-8 must satisfy 1 <= rep_min <= rep_max <= 100.",
            ImportProblem.BadIncrement("x", 50) to "increment_kg \"x\" must be a number from 0 to 50.",
            ImportProblem.SupersetLabelTooLong(20) to "superset label is longer than 20 characters.",
            ImportProblem.WrongMealCount(1, 3) to "Expected exactly 1 meal but found 3 rows.",
            ImportProblem.TooManyMeals(21, 20) to "Too many meals (21); a plan holds at most 20.",
            ImportProblem.SlotEmpty to "slot is empty.",
            ImportProblem.SlotTooLong(80) to "slot is longer than 80 characters.",
            ImportProblem.BadTime("7pm-ish") to "time \"7pm-ish\" must be 24-hour HH:mm, e.g. 07:30.",
            ImportProblem.MealNameEmpty to "meal name is empty.",
            ImportProblem.MealNameTooLong(80) to "meal name is longer than 80 characters.",
            ImportProblem.GramsEmpty("fat_g") to "fat_g is empty (use 0 if there is none).",
            ImportProblem.BadGrams("carbs_g", "lots", 1000) to "carbs_g \"lots\" must be a number from 0 to 1000.",
            ImportProblem.BadKcal("x", 5000) to "kcal \"x\" must be a number from 0 to 5000, or empty.",
            ImportProblem.BadUrl("nope") to "recipe_url \"nope\" is not a valid http(s) link.",
            ImportProblem.CouldntRead("JSONException") to "Couldn't read the file (JSONException).",
            ImportProblem.ImportFailed("IOException") to "Import failed (IOException).",
            ImportProblem.CantOpenFile to "Couldn't open the file.",
            ImportProblem.NotTextFile to "This isn't a CSV text file. Spreadsheets must be saved/exported as .csv first.",
            ImportProblem.NoPermission to "No permission to read that file.",
            ImportProblem.ProgramRefused(ProgramProblem.WorkoutInProgress) to "Finish the workout in progress from this program first",
            ImportProblem.Other("Disk full") to "Disk full",
        )
        expected.forEach { (problem, text) -> assertEquals(problem.toString(), text, say(problem)) }
        assertEquals("Line 7: day is empty.", say(ImportProblem.DayEmpty, line = 7))
    }

    @Test
    fun `every kind has its own page title with the same English as before`() {
        assertEquals(
            listOf(
                "Import a workout plan",
                "Import a workout",
                "Import a set of workouts",
                "Import an exercise",
                "Import a list of exercises",
                "Import a meal",
                "Import a list of meals",
                "Import a meal plan",
            ),
            ImportKind.entries.map { english(ImportCopy.title(it)) },
        )
    }

    @Test
    fun `the paste step and the copied snackbar name the subject`() {
        assertEquals("Paste it into the AI chat where you made your workout plan.", english(ImportCopy.pasteStep(ImportKind.PROGRAM)))
        assertEquals(
            "Prompt copied. Paste it into the AI chat where you made your list of meals.",
            english(ImportCopy.promptCopied(ImportKind.MEALS)),
        )
    }

    @Test
    fun `program problems read as before`() {
        val bench = ExerciseDefinition("bench_press", "Barbell Bench Press", StatType.STRENGTH)
        val fly = ExerciseDefinition("x_fly_1", "Fly", StatType.STRENGTH)
        val expected = listOf(
            ProgramProblem.ProgramGone to "That program no longer exists",
            ProgramProblem.DayGone to "That workout day no longer exists",
            ProgramProblem.BuiltInReadOnly to "Built-in workouts can't be changed",
            ProgramProblem.AlreadyInDay(listOf(bench, fly)) to "Already in this day: Barbell Bench Press, Fly",
            ProgramProblem.RestoreBuiltInFirst to "Restore the built-in program first",
            ProgramProblem.WorkoutInProgress to "Finish the workout in progress from this program first",
        )
        expected.forEach { (problem, text) -> assertEquals(problem.toString(), text, english(ProgramCopy.problem(problem))) }
    }

    @Test
    fun `the built-in program is named by a resource and an imported one as stored`() {
        val builtIn = Program(null, "", isActive = true, templates = ProgramCatalog.templates)
        assertEquals("Upper/Lower (built-in)", english(programNameText(builtIn)))
        assertEquals("My split", english(programNameText(builtIn.copy(id = 4L, name = "My split"))))
    }
}
