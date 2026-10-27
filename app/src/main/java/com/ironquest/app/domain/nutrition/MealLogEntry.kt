package com.ironquest.app.domain.nutrition

/** One eaten meal. [macros1x] is the snapshot taken at log time, before the portion. */
data class MealLogEntry(
    val id: Long,
    val slotId: Long?,
    val mealId: Long?,
    val mealName: String,
    val portion: Double,
    val macros1x: Macros,
    /** The foods of a quick meal ("Log as I go"); empty for slot and library logs. */
    val items: List<MealLogItem> = emptyList(),
) {
    val eaten: Macros get() = macros1x * portion
    val isQuickMeal: Boolean get() = items.isNotEmpty()
}
