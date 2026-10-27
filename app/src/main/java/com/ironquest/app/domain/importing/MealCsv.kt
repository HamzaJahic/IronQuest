package com.ironquest.app.domain.importing

import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.domain.nutrition.MealDraft
import com.ironquest.app.domain.nutrition.NutritionRules

/** One plan row: a slot of the day and the meal eaten in it. */
data class SlotImport(val label: String, val timeOfDayMin: Int?, val meal: MealDraft)

/**
 * Meal CSV: `meal,protein_g,carbs_g,fat_g,fiber_g,kcal,tags,recipe_url,recipe` (one portion
 * per row). Meal plan CSV adds `slot` and `time` in front. Only meal + P/C/F are required.
 */
object MealCsv {
    const val MAX_NAME = 80
    const val MAX_SLOTS = 20

    private const val CHICKEN_ROW = "Chicken Rice Bowl,45,60,12,4,520,lunch|high-protein,,\"Grill the chicken, cook the rice, combine.\"" // not UI text: CSV format

    val EXAMPLE_MEAL = """
        meal,protein_g,carbs_g,fat_g,fiber_g,kcal,tags,recipe_url,recipe
        $CHICKEN_ROW
    """.trimIndent()

    val EXAMPLE_MEALS = """
        meal,protein_g,carbs_g,fat_g,fiber_g,kcal,tags,recipe_url,recipe
        $CHICKEN_ROW
        Greek Yogurt & Berries,20,25,4,3,220,snack,,
        Oats with Whey,35,60,8,8,460,breakfast,,
    """.trimIndent()

    val EXAMPLE_PLAN = """
        slot,time,meal,protein_g,carbs_g,fat_g,fiber_g,kcal,tags
        Breakfast,07:30,Oats with Whey,35,60,8,8,460,breakfast
        Lunch,13:00,Chicken Rice Bowl,45,60,12,4,520,lunch
        Snack,16:30,Greek Yogurt & Berries,20,25,4,3,220,snack
        Dinner,19:30,Salmon with Potatoes,40,45,18,5,500,dinner
    """.trimIndent()

    private val ALIASES = mapOf(
        "name" to "meal", "meal_name" to "meal", "food" to "meal",
        "protein" to "protein_g", "carbs" to "carbs_g", "carbohydrates" to "carbs_g", "carbohydrates_g" to "carbs_g",
        "fat" to "fat_g", "fats" to "fat_g", "fiber" to "fiber_g", "fibre" to "fiber_g", "fibre_g" to "fiber_g",
        "calories" to "kcal", "energy" to "kcal", "energy_kcal" to "kcal", "calories_kcal" to "kcal",
        "url" to "recipe_url", "link" to "recipe_url",
        "recipe_text" to "recipe", "instructions" to "recipe", "notes" to "recipe",
        "slot_label" to "slot", "label" to "slot", "meal_slot" to "slot",
        "time_of_day" to "time",
    )

    /** [expected]: exact row count for "One meal", or null. */
    fun parseMeals(text: String, expected: Int? = null): ImportParse<List<MealDraft>> {
        val table = when (val read = Csv.read(text, ALIASES)) {
            is ImportParse.Failed -> return read
            is ImportParse.Ok -> read.value
        }
        val issues = IssueCollector()
        if (!issues.requireColumns(table, listOf("meal", "protein_g", "carbs_g", "fat_g"))) return ImportParse.Failed(issues.issues)
        if (expected != null && table.rows.size != expected) {
            return ImportParse.Failed(listOf(ImportIssue(line = null, ImportProblem.WrongMealCount(expected, table.rows.size))))
        }
        val meals = table.rows.mapNotNull { parseMeal(it, issues) }
        return if (issues.isEmpty) ImportParse.Ok(meals) else ImportParse.Failed(issues.issues)
    }

    fun parsePlan(text: String): ImportParse<List<SlotImport>> {
        val table = when (val read = Csv.read(text, ALIASES)) {
            is ImportParse.Failed -> return read
            is ImportParse.Ok -> read.value
        }
        val issues = IssueCollector()
        if (!issues.requireColumns(table, listOf("slot", "meal", "protein_g", "carbs_g", "fat_g"))) return ImportParse.Failed(issues.issues)
        if (table.rows.size > MAX_SLOTS) {
            return ImportParse.Failed(listOf(ImportIssue(line = null, ImportProblem.TooManyMeals(table.rows.size, MAX_SLOTS))))
        }
        val slots = table.rows.mapNotNull { row ->
            val label = row["slot"]
            if (label.isEmpty()) issues.add(row.line, ImportProblem.SlotEmpty)
            if (label.length > MAX_NAME) issues.add(row.line, ImportProblem.SlotTooLong(MAX_NAME))
            val timeText = row["time"]
            val time = if (timeText.isEmpty()) null else Cells.timeOfDay(timeText)
            if (timeText.isNotEmpty() && time == null) issues.add(row.line, ImportProblem.BadTime(timeText))
            val meal = parseMeal(row, issues)
            if (meal == null || label.isEmpty() || label.length > MAX_NAME || (timeText.isNotEmpty() && time == null)) {
                null
            } else {
                SlotImport(label, time, meal)
            }
        }
        return if (issues.isEmpty) ImportParse.Ok(slots) else ImportParse.Failed(issues.issues)
    }

    private fun parseMeal(row: CsvRow, issues: IssueCollector): MealDraft? {
        val start = issues.issues.size
        val name = row["meal"]
        if (name.isEmpty()) issues.add(row.line, ImportProblem.MealNameEmpty)
        if (name.length > MAX_NAME) issues.add(row.line, ImportProblem.MealNameTooLong(MAX_NAME))

        fun grams(column: String, required: Boolean): Double {
            val text = row[column]
            if (text.isEmpty()) {
                if (required) issues.add(row.line, ImportProblem.GramsEmpty(column))
                return 0.0
            }
            val grams = Cells.number(text)
            if (grams == null || grams !in 0.0..NutritionRules.MAX_MEAL_GRAMS) {
                issues.add(row.line, ImportProblem.BadGrams(column, text, NutritionRules.MAX_MEAL_GRAMS.toInt()))
                return 0.0
            }
            return grams
        }

        val protein = grams("protein_g", required = true)
        val carbs = grams("carbs_g", required = true)
        val fat = grams("fat_g", required = true)
        val fiber = grams("fiber_g", required = false)
        val kcalText = row["kcal"]
        val kcal = if (kcalText.isEmpty()) null else Cells.number(kcalText)
        if (kcalText.isNotEmpty() && (kcal == null || kcal !in 0.0..NutritionRules.MAX_KCAL)) {
            issues.add(row.line, ImportProblem.BadKcal(kcalText, NutritionRules.MAX_KCAL.toInt()))
        }
        val urlText = row["recipe_url"]
        val url = if (urlText.isEmpty()) null else NutritionRules.normalizeUrl(urlText)
        if (urlText.isNotEmpty() && url == null) issues.add(row.line, ImportProblem.BadUrl(urlText))

        if (issues.issues.size != start) return null
        return MealDraft(
            id = null,
            name = name,
            macros = Macros(protein, carbs, fat, fiber, kcal),
            tags = row["tags"].split('|', ';', ',').mapNotNull(NutritionRules::normalizeTag).distinct(),
            recipeText = row["recipe"].ifEmpty { null },
            recipeUrl = url,
        )
    }
}
