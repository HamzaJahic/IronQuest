package com.ironquest.app.domain.nutrition

enum class TargetStatus { UNDER, ON_TARGET, OVER, NO_TARGET }

data class MacroLine(val eaten: Double, val target: Double?, val status: TargetStatus) {
    /** 0..1 for progress bars; an overshoot draws a full bar. */
    val fraction: Float
        get() = if (target == null || target <= 0.0) 0f else (eaten / target).toFloat().coerceIn(0f, 1f)
}

data class DaySummary(
    val eaten: Macros,
    val protein: MacroLine,
    val carbs: MacroLine,
    val fat: MacroLine,
    val fiber: MacroLine,
    val kcal: MacroLine,
)

object DaySummaryCalculator {

    fun summarize(logs: List<MealLogEntry>, targets: MacroTargets?): DaySummary = summarize(logs.fold(Macros.ZERO) { acc, log -> acc + log.eaten }, targets)

    /** Same summary from pre-summed totals (Week/Month read SQL aggregates). */
    fun summarize(eaten: Macros, targets: MacroTargets?): DaySummary = DaySummary(
        eaten = eaten,
        protein = line(eaten.proteinG, targets?.proteinG),
        carbs = line(eaten.carbsG, targets?.carbsG),
        fat = line(eaten.fatG, targets?.fatG),
        fiber = line(eaten.fiberG, targets?.fiberG),
        kcal = line(eaten.effectiveKcal, targets?.kcal),
    )

    private fun line(eaten: Double, target: Double?) = MacroLine(eaten, target, statusOf(eaten, target))

    private const val EPS = 1e-9

    fun statusOf(eaten: Double, target: Double?): TargetStatus {
        if (target == null || target <= 0.0) return TargetStatus.NO_TARGET
        val low = target * (1 - NutritionRules.ON_TARGET_TOLERANCE)
        val high = target * (1 + NutritionRules.ON_TARGET_TOLERANCE)
        return when {
            eaten < low - EPS -> TargetStatus.UNDER
            eaten > high + EPS -> TargetStatus.OVER
            else -> TargetStatus.ON_TARGET
        }
    }
}
