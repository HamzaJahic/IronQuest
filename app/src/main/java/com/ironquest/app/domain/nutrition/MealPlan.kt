package com.ironquest.app.domain.nutrition

data class PlanSlot(
    val id: Long,
    val label: String,
    val mealId: Long,
    val mealName: String,
    val mealMacros: Macros,
    /** Minutes after midnight, 0..1439. */
    val timeOfDayMin: Int?,
    /** One of [NutritionRules.REMINDER_OFFSETS]; only set when [timeOfDayMin] is. */
    val reminderOffsetMin: Int?,
)

data class MealPlan(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val targets: MacroTargets,
    val slots: List<PlanSlot>,
)

/** `id == null` inserts a new slot; an existing id is updated in place (never re-inserted). */
data class SlotDraft(
    val id: Long?,
    val label: String,
    val mealId: Long,
    val timeOfDayMin: Int?,
    val reminderOffsetMin: Int?,
)

data class PlanDraft(
    val id: Long?,
    val name: String,
    val targets: MacroTargets,
    val slots: List<SlotDraft>,
)
