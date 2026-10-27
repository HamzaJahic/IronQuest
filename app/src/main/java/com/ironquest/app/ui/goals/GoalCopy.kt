package com.ironquest.app.ui.goals

import com.ironquest.app.R
import com.ironquest.app.domain.goals.BeatLastTimeGoal
import com.ironquest.app.domain.goals.FoodGoal
import com.ironquest.app.domain.goals.FoodTarget
import com.ironquest.app.domain.goals.Goal
import com.ironquest.app.domain.goals.GoalType
import com.ironquest.app.domain.goals.LimitBreakGoal
import com.ironquest.app.domain.goals.WorkoutGoal
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.ui.catalog.exerciseNameText
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.joinedText
import com.ironquest.app.ui.text.uiText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Plain copy for goals, shared by Today, the workout stamps and notifications. Pure: it returns
 * [UiText] and formats numbers with the default locale, so it runs in JVM tests.
 */
object GoalCopy {

    fun label(type: GoalType): UiText = when (type) {
        GoalType.LIMIT_BREAK -> uiText(R.string.goal_limit_break)
        GoalType.BEAT_LAST_TIME -> uiText(R.string.goal_beat_last_time)
        GoalType.FOOD -> uiText(R.string.goal_food)
    }

    fun title(goal: Goal): UiText = when (goal) {
        is LimitBreakGoal -> uiText(R.string.goal_limit_break_title, exerciseName(goal), kg(goal.targetWeightKg))
        is BeatLastTimeGoal -> if (goal.exercise.exercise.trackingType == TrackingType.DURATION) {
            uiText(R.string.goal_beat_last_time_title_minute, exerciseName(goal))
        } else {
            uiText(R.string.goal_beat_last_time_title_rep, exerciseName(goal))
        }
        is FoodGoal -> when {
            goal.target == FoodTarget.PROTEIN && goal.isDone -> uiText(R.string.goal_food_protein_done)
            goal.target == FoodTarget.PROTEIN -> uiText(R.string.goal_food_protein_title, grams(goal.remaining))
            goal.isDone -> uiText(R.string.goal_food_water_done)
            else -> uiText(R.string.goal_food_water_title, water(goal.remaining))
        }
    }

    fun detail(goal: Goal): UiText = when (goal) {
        is WorkoutGoal -> uiText(R.string.goal_detail_last_time, sets(goal.lastSets, goal.exercise.exercise.trackingType))
        is FoodGoal -> when (goal.target) {
            FoodTarget.PROTEIN -> uiText(R.string.goal_detail_protein, goal.amount.roundToInt(), grams(goal.goal))
            FoodTarget.WATER -> uiText(R.string.goal_detail_water, water(goal.amount), water(goal.goal))
        }
    }

    /** "80 kg × 8, 8, 7" / "12, 10 reps" / "30 min" / "70 kg × 9, 72.5 kg × 8". */
    fun sets(sets: List<LoggedSet>, trackingType: TrackingType): UiText {
        if (sets.isEmpty()) return uiText(R.string.goal_sets_none)
        if (trackingType == TrackingType.DURATION) {
            return uiText(R.string.unit_min_value, numbers(sets.map { it.durationMinutes ?: 0 }))
        }
        val weights = sets.map { it.weightKg }.distinct()
        return when {
            weights.size == 1 && weights[0] <= 0.0 -> uiText(R.string.goal_sets_reps, numbers(sets.map { it.reps }))
            weights.size == 1 -> uiText(R.string.goal_sets_weight_reps, kg(weights[0]), numbers(sets.map { it.reps }))
            else -> joinedText(
                sets.map { uiText(R.string.goal_sets_weight_reps, kg(it.weightKg), number(it.reps)) },
                R.string.common_list_separator,
            )
        }
    }

    /** Stamp text when a goal is reached during a workout. */
    fun stamp(type: GoalType): UiText = label(type)

    /** One line per notification: "Limit break: Bench press 82.5 kg · Beat last time: Barbell row". */
    fun workoutNotificationBody(goals: List<WorkoutGoal>): UiText = joinedText(
        goals.groupBy { it.type }.entries.map { (type, list) ->
            uiText(
                R.string.goal_notification_group,
                label(type),
                joinedText(
                    list.map { goal ->
                        if (goal is LimitBreakGoal) {
                            uiText(R.string.goal_notification_limit_break_item, exerciseName(goal), kg(goal.targetWeightKg))
                        } else {
                            exerciseName(goal)
                        }
                    },
                    R.string.common_list_separator,
                ),
            )
        },
        R.string.common_dot_separator,
    )

    fun foodNotificationTitle(open: List<FoodGoal>): UiText = when {
        open.size > 1 -> uiText(R.string.goal_notification_food_open_many)
        open.firstOrNull()?.target == FoodTarget.WATER -> uiText(R.string.goal_notification_water_open)
        else -> uiText(R.string.goal_notification_protein_open)
    }

    fun foodNotificationBody(open: List<FoodGoal>): UiText = joinedText(
        open.map { goal ->
            when (goal.target) {
                FoodTarget.PROTEIN -> uiText(R.string.goal_notification_protein_left, grams(goal.remaining))
                FoodTarget.WATER -> uiText(R.string.goal_notification_water_left, water(goal.remaining))
            }
        },
        R.string.common_dot_separator,
    )

    fun grams(value: Double): UiText = uiText(R.string.unit_g_value, value.roundToInt())

    /** 750 → "750 ml", 1250 → "1.25 l". */
    fun water(ml: Double): UiText {
        val whole = ml.roundToInt()
        return if (whole < 1000) uiText(R.string.unit_ml_value, whole) else uiText(R.string.unit_l_value, decimal(whole / 1000.0))
    }

    private fun exerciseName(goal: WorkoutGoal): UiText = exerciseNameText(goal.exercise.exercise)

    private fun kg(value: Double): UiText = uiText(R.string.unit_kg_value, decimal(value))

    private fun decimal(value: Double): String = Formats.decimal(value, Locale.getDefault(), maxDecimals = 3)

    private fun number(value: Int): UiText = UiText.Verbatim(Formats.integer(value, Locale.getDefault()))

    private fun numbers(values: List<Int>): UiText = joinedText(values.map(::number), R.string.common_list_separator)
}
