package com.ironquest.app.domain.nutrition

import java.time.LocalDate

data class DayNutrition(
    val date: LocalDate,
    val activePlan: MealPlan?,
    /** Live active-plan targets for today; the stored snapshot for past days; null if none. */
    val targets: MacroTargets?,
    val logs: List<MealLogEntry>,
    val waterMl: Int,
    val supplements: List<SupplementStatus>,
)
