package com.ironquest.app.ui.onboarding

import androidx.annotation.StringRes
import com.ironquest.app.R
import com.ironquest.app.domain.onboarding.OnboardingTopic

/**
 * The words of every tip: (title, body) per step, indexed by the step's position in
 * [OnboardingTopic.steps] (not in the filtered list, so a skipped step keeps its words).
 */
object OnboardingCopy {

    @StringRes
    fun title(topic: OnboardingTopic, stepIndex: Int): Int = steps(topic)[stepIndex].first

    @StringRes
    fun body(topic: OnboardingTopic, stepIndex: Int): Int = steps(topic)[stepIndex].second

    fun steps(topic: OnboardingTopic): List<Pair<Int, Int>> = when (topic) {
        OnboardingTopic.WELCOME -> listOf(
            R.string.onboarding_welcome_0_title to R.string.onboarding_welcome_0_body,
            R.string.onboarding_welcome_1_title to R.string.onboarding_welcome_1_body,
            R.string.onboarding_welcome_2_title to R.string.onboarding_welcome_2_body,
        )
        OnboardingTopic.TODAY -> listOf(
            R.string.onboarding_today_0_title to R.string.onboarding_today_0_body,
            R.string.onboarding_today_1_title to R.string.onboarding_today_1_body,
            R.string.onboarding_today_2_title to R.string.onboarding_today_2_body,
            R.string.onboarding_today_3_title to R.string.onboarding_today_3_body,
            R.string.onboarding_today_4_title to R.string.onboarding_today_4_body,
        )
        OnboardingTopic.GOALS -> listOf(
            R.string.onboarding_goals_0_title to R.string.onboarding_goals_0_body,
        )
        OnboardingTopic.WORKOUT -> listOf(
            R.string.onboarding_workout_0_title to R.string.onboarding_workout_0_body,
            R.string.onboarding_workout_1_title to R.string.onboarding_workout_1_body,
            R.string.onboarding_workout_2_title to R.string.onboarding_workout_2_body,
            R.string.onboarding_workout_3_title to R.string.onboarding_workout_3_body,
            R.string.onboarding_workout_4_title to R.string.onboarding_workout_4_body,
        )
        OnboardingTopic.WORKOUT_LOGGED_SET -> listOf(
            R.string.onboarding_workout_logged_set_0_title to R.string.onboarding_workout_logged_set_0_body,
            R.string.onboarding_workout_logged_set_1_title to R.string.onboarding_workout_logged_set_1_body,
        )
        OnboardingTopic.SUPERSET -> listOf(
            R.string.onboarding_superset_0_title to R.string.onboarding_superset_0_body,
            R.string.onboarding_superset_1_title to R.string.onboarding_superset_1_body,
        )
        OnboardingTopic.LOG -> listOf(
            R.string.onboarding_log_0_title to R.string.onboarding_log_0_body,
        )
        OnboardingTopic.LOG_DAY -> listOf(
            R.string.onboarding_log_day_0_title to R.string.onboarding_log_day_0_body,
            R.string.onboarding_log_day_1_title to R.string.onboarding_log_day_1_body,
        )
        OnboardingTopic.LOG_EDITOR -> listOf(
            R.string.onboarding_log_editor_0_title to R.string.onboarding_log_editor_0_body,
            R.string.onboarding_log_editor_1_title to R.string.onboarding_log_editor_1_body,
        )
        OnboardingTopic.FOOD_DAY -> listOf(
            R.string.onboarding_food_day_0_title to R.string.onboarding_food_day_0_body,
            R.string.onboarding_food_day_1_title to R.string.onboarding_food_day_1_body,
            R.string.onboarding_food_day_2_title to R.string.onboarding_food_day_2_body,
            R.string.onboarding_food_day_3_title to R.string.onboarding_food_day_3_body,
            R.string.onboarding_food_day_4_title to R.string.onboarding_food_day_4_body,
            R.string.onboarding_food_day_5_title to R.string.onboarding_food_day_5_body,
            R.string.onboarding_food_day_6_title to R.string.onboarding_food_day_6_body,
            R.string.onboarding_food_day_7_title to R.string.onboarding_food_day_7_body,
            R.string.onboarding_food_day_8_title to R.string.onboarding_food_day_8_body,
        )
        OnboardingTopic.FOOD_OVERVIEW -> listOf(
            R.string.onboarding_food_overview_0_title to R.string.onboarding_food_overview_0_body,
            R.string.onboarding_food_overview_1_title to R.string.onboarding_food_overview_1_body,
            R.string.onboarding_food_overview_2_title to R.string.onboarding_food_overview_2_body,
        )
        OnboardingTopic.MEAL_BUILDER -> listOf(
            R.string.onboarding_meal_builder_0_title to R.string.onboarding_meal_builder_0_body,
            R.string.onboarding_meal_builder_1_title to R.string.onboarding_meal_builder_1_body,
            R.string.onboarding_meal_builder_2_title to R.string.onboarding_meal_builder_2_body,
        )
        OnboardingTopic.MEAL_BUILDER_AMOUNT -> listOf(
            R.string.onboarding_meal_builder_amount_0_title to R.string.onboarding_meal_builder_amount_0_body,
        )
        OnboardingTopic.CUSTOM_FOOD -> listOf(
            R.string.onboarding_custom_food_0_title to R.string.onboarding_custom_food_0_body,
        )
        OnboardingTopic.MEALS -> listOf(
            R.string.onboarding_meals_0_title to R.string.onboarding_meals_0_body,
            R.string.onboarding_meals_1_title to R.string.onboarding_meals_1_body,
            R.string.onboarding_meals_2_title to R.string.onboarding_meals_2_body,
        )
        OnboardingTopic.MEAL_DETAIL -> listOf(
            R.string.onboarding_meal_detail_0_title to R.string.onboarding_meal_detail_0_body,
        )
        OnboardingTopic.MEAL_EDITOR -> listOf(
            R.string.onboarding_meal_editor_0_title to R.string.onboarding_meal_editor_0_body,
            R.string.onboarding_meal_editor_1_title to R.string.onboarding_meal_editor_1_body,
        )
        OnboardingTopic.INGREDIENTS -> listOf(
            R.string.onboarding_ingredients_0_title to R.string.onboarding_ingredients_0_body,
            R.string.onboarding_ingredients_1_title to R.string.onboarding_ingredients_1_body,
        )
        OnboardingTopic.PLANS -> listOf(
            R.string.onboarding_plans_0_title to R.string.onboarding_plans_0_body,
            R.string.onboarding_plans_1_title to R.string.onboarding_plans_1_body,
            R.string.onboarding_plans_2_title to R.string.onboarding_plans_2_body,
        )
        OnboardingTopic.PLAN_EDITOR -> listOf(
            R.string.onboarding_plan_editor_0_title to R.string.onboarding_plan_editor_0_body,
            R.string.onboarding_plan_editor_1_title to R.string.onboarding_plan_editor_1_body,
        )
        OnboardingTopic.PROGRAMS -> listOf(
            R.string.onboarding_programs_0_title to R.string.onboarding_programs_0_body,
            R.string.onboarding_programs_1_title to R.string.onboarding_programs_1_body,
            R.string.onboarding_programs_2_title to R.string.onboarding_programs_2_body,
        )
        OnboardingTopic.IMPORT -> listOf(
            R.string.onboarding_import_0_title to R.string.onboarding_import_0_body,
            R.string.onboarding_import_1_title to R.string.onboarding_import_1_body,
            R.string.onboarding_import_2_title to R.string.onboarding_import_2_body,
        )
        OnboardingTopic.YOU -> listOf(
            R.string.onboarding_you_0_title to R.string.onboarding_you_0_body,
            R.string.onboarding_you_1_title to R.string.onboarding_you_1_body,
            R.string.onboarding_you_2_title to R.string.onboarding_you_2_body,
            R.string.onboarding_you_3_title to R.string.onboarding_you_3_body,
            R.string.onboarding_you_4_title to R.string.onboarding_you_4_body,
            R.string.onboarding_you_5_title to R.string.onboarding_you_5_body,
        )
    }
}
