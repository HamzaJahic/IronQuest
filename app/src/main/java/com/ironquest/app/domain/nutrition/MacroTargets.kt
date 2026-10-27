package com.ironquest.app.domain.nutrition

/** Daily targets; every field is optional. */
data class MacroTargets(
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val kcal: Double? = null,
    val waterMl: Int? = null,
)
