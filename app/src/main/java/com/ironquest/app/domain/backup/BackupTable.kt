package com.ironquest.app.domain.backup

/**
 * Tables a backup carries, in insert order (parents before children). Exercises are only added,
 * never replaced: the built-in ones are re-seeded from the catalog on every database open.
 * [since] is the format version that introduced the table; older files simply have none of its rows.
 */
enum class BackupTable(val sqlName: String, val since: Int = 1, val replaced: Boolean = true) {
    PLAYER_PROFILE("player_profile"),
    EXERCISES("exercises", replaced = false),
    PROGRAMS("programs"),
    PROGRAM_DAYS("program_days"),
    PROGRAM_EXERCISES("program_exercises"),
    MEALS("meals"),
    MEAL_TAGS("meal_tags"),
    USER_FOODS("user_foods", since = 2),
    MEAL_PLANS("meal_plans"),
    PLAN_SLOTS("plan_slots"),
    SUPPLEMENTS("supplements"),
    SUPPLEMENT_INTAKES("supplement_intakes"),
    WORKOUTS("workouts"),
    SETS("sets"),
    NUTRITION_DAYS("nutrition_days"),
    MEAL_LOGS("meal_logs"),
    MEAL_LOG_ITEMS("meal_log_items", since = 2),
}
