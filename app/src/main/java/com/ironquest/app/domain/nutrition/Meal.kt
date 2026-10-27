package com.ironquest.app.domain.nutrition

data class Meal(
    val id: Long,
    val name: String,
    val macros: Macros,
    val tags: List<String>,
    val recipeText: String?,
    val recipeUrl: String?,
    /** Hidden from the library and pickers; plans and logs may still point at it. */
    val archived: Boolean = false,
)

/** `id == null` creates a new meal. Strings are expected to be validated/normalised already. */
data class MealDraft(
    val id: Long?,
    val name: String,
    val macros: Macros,
    val tags: List<String>,
    val recipeText: String?,
    val recipeUrl: String?,
)

/** "CUT · Lunch · 13:00" line on the meal detail screen. */
data class MealUsage(val planName: String, val slotLabel: String, val timeOfDayMin: Int?)
