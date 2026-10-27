package com.ironquest.app.domain.nutrition

/** Grams of each macro plus calories. `kcal == null` means "derive from P/C/F". */
data class Macros(
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val kcal: Double? = null,
) {
    val effectiveKcal: Double get() = kcal ?: (proteinG * 4 + carbsG * 4 + fatG * 9)

    /** Scales by a portion multiplier. The result always carries resolved kcal. */
    operator fun times(portion: Double) = Macros(
        proteinG = proteinG * portion,
        carbsG = carbsG * portion,
        fatG = fatG * portion,
        fiberG = fiberG * portion,
        kcal = effectiveKcal * portion,
    )

    operator fun plus(other: Macros) = Macros(
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
        fiberG = fiberG + other.fiberG,
        kcal = effectiveKcal + other.effectiveKcal,
    )

    companion object {
        val ZERO = Macros(kcal = 0.0)
    }
}
