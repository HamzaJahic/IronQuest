package com.ironquest.app.domain.importing

import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.domain.nutrition.Meal
import com.ironquest.app.domain.nutrition.PlanSlot
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Writes plans in the same CSV the importers read, so a plan shared as text can be imported
 * on another phone ("Whole plan" for workouts, "Meal plan" for meals) and round-trips exactly.
 * Pure: no Android, no I/O.
 */
object CsvExport {

    /**
     * A workout program, one row per exercise. Superset members get the day's superset letter
     * (A, B, … in the order they appear), as the importer expects consecutive rows per label.
     */
    fun program(days: List<WorkoutTemplate>): String = buildString {
        append(WorkoutCsv.HEADER).append('\n')
        for (day in days) {
            for (block in day.blocks) {
                val label = (block as? WorkoutBlock.Superset)?.letter.orEmpty()
                for (templateExercise in block.exercises) {
                    val definition = templateExercise.exercise
                    append(
                        row(
                            day.name,
                            definition.name,
                            templateExercise.targetSets.toString(),
                            templateExercise.repRange.min.toString(),
                            templateExercise.repRange.max.toString(),
                            if (definition.trackingType == TrackingType.DURATION) "minutes" else "reps",
                            definition.stat.csvCode,
                            number(definition.incrementKg),
                            label,
                        ),
                    ).append('\n')
                }
            }
        }
    }

    /**
     * A meal plan, one row per slot in plan order. [meal] is the library meal behind the slot
     * (for tags and recipe); when it is gone the slot's own macro snapshot is written.
     * Daily targets are not exported: the importer derives them from the meals.
     */
    fun mealPlan(slots: List<Pair<PlanSlot, Meal?>>): String = buildString {
        append("slot,time,meal,protein_g,carbs_g,fat_g,fiber_g,kcal,tags,recipe_url,recipe\n") // not UI text: CSV format
        for ((slot, meal) in slots) {
            val macros = meal?.macros ?: slot.mealMacros
            append(
                row(
                    slot.label,
                    slot.timeOfDayMin?.let { String.format(Locale.ROOT, "%02d:%02d", it / 60, it % 60) }.orEmpty(),
                    meal?.name ?: slot.mealName,
                    number(macros.proteinG),
                    number(macros.carbsG),
                    number(macros.fatG),
                    number(macros.fiberG),
                    number(Math.round(macros.effectiveKcal).toDouble()),
                    meal?.tags?.joinToString("|").orEmpty(),
                    meal?.recipeUrl.orEmpty(),
                    meal?.recipeText.orEmpty(),
                ),
            ).append('\n')
        }
    }

    /** "2.5", "80", never "2.50" or "80.0". */
    internal fun number(value: Double): String = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun row(vararg cells: String) = cells.joinToString(",") { cell(it) }

    /** RFC 4180: quote when the text holds a comma, quote, line break or edge spaces. */
    internal fun cell(text: String): String = if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || text != text.trim()) {
        "\"" + text.replace("\"", "\"\"") + "\"" // not UI text: CSV format
    } else {
        text
    }
}
