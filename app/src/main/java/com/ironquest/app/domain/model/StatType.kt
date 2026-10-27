package com.ironquest.app.domain.model

/** RPG attributes that exercises feed XP into. */
/** [csvCode] is the `stat` column of the workout CSV (a file format, not UI text); display names are in `ui/you/StatLabels.kt`. */
enum class StatType(val csvCode: String) {
    CONSTITUTION("CON"), // not UI text: legs: squats, deadlifts, lunges…
    STRENGTH("STR"), // not UI text: upper body: bench, rows, presses…
    STAMINA("STA"), // not UI text: conditioning: cycling
}
