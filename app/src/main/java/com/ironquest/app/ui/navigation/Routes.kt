package com.ironquest.app.ui.navigation

import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.ui.food.MealDetailViewModel
import com.ironquest.app.ui.food.MealEditorViewModel
import com.ironquest.app.ui.food.PlanEditorViewModel
import com.ironquest.app.ui.food.builder.MealBuilderViewModel
import com.ironquest.app.ui.imports.ImportViewModel
import com.ironquest.app.ui.workout.ActiveWorkoutViewModel
import com.ironquest.app.ui.workout.WorkoutLogEditorViewModel
import java.time.LocalDate

object Routes {
    const val TODAY = "today"
    const val LOG = "log"
    const val YOU = "you"
    private const val WORKOUT = "workout"
    const val WORKOUT_PATTERN = "$WORKOUT/{${ActiveWorkoutViewModel.ARG_TEMPLATE_ID}}" // not UI text: navigation route
    fun workout(templateId: String) = "$WORKOUT/$templateId" // not UI text: navigation route

    /** Add a finished workout to [date] (workoutId 0) or edit a stored one. */
    const val WORKOUT_LOG_PATTERN = "workoutlog/{${WorkoutLogEditorViewModel.ARG_WORKOUT_ID}}/{${WorkoutLogEditorViewModel.ARG_TEMPLATE_ID}}/{${WorkoutLogEditorViewModel.ARG_DATE}}" // not UI text: navigation route
    fun workoutLog(workoutId: Long, templateId: String, date: LocalDate) = "workoutlog/$workoutId/$templateId/${date.toEpochDay()}" // not UI text: navigation route

    const val FOOD = "food"
    const val MEALS = "meals"
    const val PLANS = "plans"
    private const val NEW = "new"
    const val MEAL_DETAIL_PATTERN = "meal/{${MealDetailViewModel.ARG_MEAL_ID}}" // not UI text: navigation route
    const val MEAL_EDIT_PATTERN = "meal/edit/{${MealEditorViewModel.ARG_MEAL_ID}}" // not UI text: navigation route
    const val PLAN_EDIT_PATTERN = "plan/edit/{${PlanEditorViewModel.ARG_PLAN_ID}}" // not UI text: navigation route
    fun mealDetail(id: Long) = "meal/$id" // not UI text: navigation route
    fun mealEdit(id: Long?) = "meal/edit/${id ?: NEW}" // not UI text: navigation route
    fun planEdit(id: Long?) = "plan/edit/${id ?: NEW}" // not UI text: navigation route

    /** Log food on [date]: logId 0 starts a new quick meal, otherwise edits that log. */
    const val MEAL_BUILDER_PATTERN = "food/log/{${MealBuilderViewModel.ARG_DATE}}/{${MealBuilderViewModel.ARG_LOG_ID}}" // not UI text: navigation route
    fun mealBuilder(date: LocalDate, logId: Long?) = "food/log/${date.toEpochDay()}/${logId ?: 0}" // not UI text: navigation route

    /** First-launch food setup, and the same screen as a settings page (argument fromSettings = true). */
    const val FOOD_SETUP = "setup/food" // not UI text: navigation route
    const val FOOD_SETTINGS = "settings/food" // not UI text: navigation route

    const val PROGRAMS = "programs"
    const val IMPORT_PATTERN = "import/{${ImportViewModel.ARG_KIND}}" // not UI text: navigation route
    fun importCsv(kind: ImportKind) = "import/${kind.name}" // not UI text: navigation route

    /** Notifications posted before the tab rename still carry the old routes. */
    private val legacyTabRoutes = mapOf("hub" to TODAY, "chronicle" to LOG, "rations" to FOOD)

    fun fromLegacy(route: String): String = legacyTabRoutes[route] ?: route
}
