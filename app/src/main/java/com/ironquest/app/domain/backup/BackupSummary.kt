package com.ironquest.app.domain.backup

/** What the user sees before replacing their data with a backup. */
data class BackupSummary(
    val exportedAtMillis: Long,
    val workouts: Int,
    val meals: Int,
    val mealPlans: Int,
    val programs: Int,
    val mealLogs: Int,
)
