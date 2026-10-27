package com.ironquest.app.ui.catalog

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.ironquest.app.R
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.WorkoutTemplate
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.resolve

/**
 * Display names of the built-in exercises and days, keyed by `ProgramCatalog` id. Imported
 * exercises and days are not here: they show as stored. The stored (English) name stays in the
 * DB mirror, CSV export, import matching and backups.
 */
object CatalogNames {
    @StringRes
    fun exercise(id: String): Int? = when (id) {
        "bench_press" -> R.string.exercise_bench_press
        "barbell_row" -> R.string.exercise_barbell_row
        "overhead_press" -> R.string.exercise_overhead_press
        "lat_pulldown" -> R.string.exercise_lat_pulldown
        "barbell_curl" -> R.string.exercise_barbell_curl
        "incline_bench" -> R.string.exercise_incline_bench
        "cable_row" -> R.string.exercise_cable_row
        "triceps_pushdown" -> R.string.exercise_triceps_pushdown
        "back_squat" -> R.string.exercise_back_squat
        "romanian_deadlift" -> R.string.exercise_romanian_deadlift
        "leg_press" -> R.string.exercise_leg_press
        "leg_curl" -> R.string.exercise_leg_curl
        "calf_raise" -> R.string.exercise_calf_raise
        "deadlift" -> R.string.exercise_deadlift
        "front_squat" -> R.string.exercise_front_squat
        "bulgarian_split_squat" -> R.string.exercise_bulgarian_split_squat
        "leg_extension" -> R.string.exercise_leg_extension
        "cycling" -> R.string.exercise_cycling
        else -> null
    }

    @StringRes
    fun template(id: String): Int? = when (id) {
        "UPPER_1" -> R.string.template_upper_1 // not UI text: template id keys
        "LOWER_1" -> R.string.template_lower_1 // not UI text: template id keys
        "UPPER_2" -> R.string.template_upper_2 // not UI text: template id keys
        "LOWER_2" -> R.string.template_lower_2 // not UI text: template id keys
        else -> null
    }
}

fun exerciseNameText(exercise: ExerciseDefinition): UiText = exerciseNameText(exercise.id, exercise.name)

/** Exercise name from an id and its stored name (history rows carry only those). */
fun exerciseNameText(exerciseId: String, storedName: String): UiText = CatalogNames.exercise(exerciseId)?.let { UiText.Res(it) } ?: UiText.Verbatim(storedName)

fun templateNameText(template: WorkoutTemplate): UiText = templateNameText(template.id, template.name)

/** Template name from an id and its stored name (history rows carry only those). */
fun templateNameText(templateId: String, storedName: String): UiText = CatalogNames.template(templateId)?.let { UiText.Res(it) } ?: UiText.Verbatim(storedName)

@Composable
fun exerciseName(exercise: ExerciseDefinition): String {
    LocalConfiguration.current // re-resolve when the app language changes
    return exerciseNameText(exercise).resolve(LocalContext.current.resources)
}

@Composable
fun exerciseName(exerciseId: String, storedName: String): String {
    LocalConfiguration.current
    return exerciseNameText(exerciseId, storedName).resolve(LocalContext.current.resources)
}

@Composable
fun templateName(template: WorkoutTemplate): String = templateName(template.id, template.name)

@Composable
fun templateName(templateId: String, storedName: String): String {
    LocalConfiguration.current
    return templateNameText(templateId, storedName).resolve(LocalContext.current.resources)
}

/** Name of a program: the built-in split (`id == null`) has no stored name, it is a resource. */
fun programNameText(program: Program): UiText = if (program.isBuiltIn) UiText.Res(R.string.program_built_in_name) else UiText.Verbatim(program.name)

@Composable
fun programName(program: Program): String {
    LocalConfiguration.current
    return programNameText(program).resolve(LocalContext.current.resources)
}
