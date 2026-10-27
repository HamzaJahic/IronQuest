package com.ironquest.app.domain.importing

import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.PlanSlot
import com.ironquest.app.domain.program.ProgramCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exported plans must import back unchanged: that is what makes sharing them work. */
class CsvExportTest {

    private fun parsedDays(text: String) = (WorkoutCsv.parse(text, requireDay = true) as ImportParse.Ok).value

    @Test
    fun `the built-in program round-trips through the workout importer`() {
        val days = parsedDays(CsvExport.program(ProgramCatalog.templates))
        assertEquals(ProgramCatalog.templates.map { it.name }, days.map { it.name })
        ProgramCatalog.templates.zip(days).forEach { (template, day) ->
            assertEquals(template.exercises.map { it.exercise.id }, day.exercises.map { it.definition.id })
            assertEquals(template.exercises.map { it.targetSets }, day.exercises.map { it.targetSets })
            assertEquals(template.exercises.map { it.repRange }, day.exercises.map { it.repRange })
        }
    }

    @Test
    fun `supersets, cardio, custom names and commas survive`() {
        val fly = ExerciseDefinition("x_fly", "Fly, cable \"low\"", StatType.STRENGTH, incrementKg = 1.25)
        val row = ExerciseDefinition("x_row", "Seated row", StatType.STRENGTH)
        val bike = ExerciseDefinition("x_bike", "Bike", StatType.STAMINA, TrackingType.DURATION, 0.0)
        val day = WorkoutTemplate(
            id = "C1",
            name = "Push, pull",
            exercises = listOf(
                TemplateExercise(fly, 3, RepRange(8, 12), supersetGroup = "g1"),
                TemplateExercise(row, 3, RepRange(8, 12), supersetGroup = "g1"),
                TemplateExercise(bike, 1, RepRange(10, 20)),
            ),
        )
        val parsed = parsedDays(CsvExport.program(listOf(day))).single()
        assertEquals("Push, pull", parsed.name)
        assertEquals(listOf("Fly, cable \"low\"", "Seated row", "Bike"), parsed.exercises.map { it.definition.name })
        assertEquals(1.25, parsed.exercises[0].definition.incrementKg, 0.0)
        assertEquals(TrackingType.DURATION, parsed.exercises[2].definition.trackingType)
        // Members of one superset keep a shared label; the standalone one has none.
        assertEquals(parsed.exercises[0].supersetGroup, parsed.exercises[1].supersetGroup)
        assertTrue(parsed.exercises[0].supersetGroup != null)
        assertEquals(null, parsed.exercises[2].supersetGroup)
    }

    @Test
    fun `numbers have no trailing zeros`() {
        assertEquals("2.5", CsvExport.number(2.5))
        assertEquals("80", CsvExport.number(80.0))
        assertEquals("0", CsvExport.number(0.0))
        assertEquals("100", CsvExport.number(100.0))
    }

    @Test
    fun `meal plan round-trips with tags and recipe`() {
        val oats = Meal(1, "Oats, with whey", Macros(35.0, 60.0, 8.0, 8.0, 460.0), listOf("breakfast", "high-protein"), "Mix.\nServe warm.", "https://example.com/oats")
        val bowl = Meal(2, "Chicken Rice Bowl", Macros(45.0, 60.0, 12.0, 4.0), emptyList(), null, null)
        val slots = listOf(
            PlanSlot(10, "Breakfast", 1, oats.name, oats.macros, 450, null) to oats,
            PlanSlot(11, "Lunch", 2, bowl.name, bowl.macros, null, null) to bowl,
        )
        val parsed = (MealCsv.parsePlan(CsvExport.mealPlan(slots)) as ImportParse.Ok).value
        assertEquals(listOf("Breakfast", "Lunch"), parsed.map { it.label })
        assertEquals(listOf(450, null), parsed.map { it.timeOfDayMin })
        val first = parsed[0].meal
        assertEquals("Oats, with whey", first.name)
        assertEquals(35.0, first.macros.proteinG, 0.0)
        assertEquals(460.0, checkNotNull(first.macros.kcal), 0.0)
        assertEquals(listOf("breakfast", "high-protein"), first.tags)
        assertEquals("Mix.\nServe warm.", first.recipeText)
        assertEquals("https://example.com/oats", first.recipeUrl)
        // kcal is written resolved (4P + 4C + 9F), so the friend sees the same number.
        assertEquals(bowl.macros.effectiveKcal, checkNotNull(parsed[1].meal.macros.kcal), 0.5)
    }

    @Test
    fun `a slot whose meal was deleted exports its snapshot`() {
        val snapshot = Macros(20.0, 25.0, 4.0, 3.0, 220.0)
        val csv = CsvExport.mealPlan(listOf(PlanSlot(1, "Snack", 9, "Yogurt", snapshot, 990, null) to null))
        val meal = (MealCsv.parsePlan(csv) as ImportParse.Ok).value.single()
        assertEquals("Yogurt", meal.meal.name)
        assertEquals(990, meal.timeOfDayMin)
        assertEquals(20.0, meal.meal.macros.proteinG, 0.0)
    }
}
