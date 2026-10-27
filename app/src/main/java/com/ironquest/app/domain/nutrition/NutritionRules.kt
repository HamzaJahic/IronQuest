package com.ironquest.app.domain.nutrition

import java.net.URI

/** Result of parsing an optional numeric text field. */
sealed interface FieldValue {
    data object Blank : FieldValue
    data class Value(val value: Double) : FieldValue
    data object Invalid : FieldValue
}

/** Every nutrition number lives here so balancing is a one-file change. */
object NutritionRules {
    const val MEAL_XP = 5
    const val PROTEIN_BONUS_XP = 25
    const val WATER_BONUS_XP = 10
    const val SUPPLEMENT_XP = 2

    val REMINDER_OFFSETS = listOf(5, 10, 15, 30, 60)

    const val MIN_PORTION = 0.25
    const val MAX_PORTION = 5.0
    const val MAX_MEAL_GRAMS = 1_000.0
    const val MAX_TARGET_GRAMS = 2_000.0
    const val MAX_KCAL = 10_000.0
    const val MAX_WATER_ML = 10_000.0
    const val WATER_STEP_ML = 250

    /** Most water one day can hold; `addWater` clamps to it. */
    const val MAX_WATER_DAY_ML = 10_000

    /** "On target" = within ±10 % of the target, inclusive. */
    const val ON_TARGET_TOLERANCE = 0.10

    private const val EPS = 1e-9
    private val PLAIN_DECIMAL = Regex("^\\d+([.,]\\d+)?$")
    private val THOUSANDS = Regex("^\\d{1,3},\\d{3}$")
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://") // not UI text

    fun proteinBonus(eatenProteinG: Double, targets: MacroTargets?): Int {
        val target = targets?.proteinG ?: return 0
        return if (target > 0 && eatenProteinG >= target - EPS) PROTEIN_BONUS_XP else 0
    }

    fun waterBonus(waterMl: Int, targets: MacroTargets?): Int {
        val target = targets?.waterMl ?: return 0
        return if (target > 0 && waterMl >= target) WATER_BONUS_XP else 0
    }

    /**
     * New day total after adding [deltaMl] (may be negative): never below 0 and never raised
     * above [MAX_WATER_DAY_ML]. A total already above the cap can still be lowered.
     */
    fun clampWater(currentMl: Int, deltaMl: Int): Int = (currentMl + deltaMl).coerceAtMost(maxOf(currentMl, MAX_WATER_DAY_ML)).coerceAtLeast(0)

    /**
     * Supplement-intake reads (streaks) only look back this many days, so their cost stays
     * bounded; a displayed streak therefore caps at 400.
     */
    const val STREAK_WINDOW_DAYS = 400

    /** First epoch day of the [STREAK_WINDOW_DAYS]-day window that ends at [lastEpochDay] (inclusive). */
    fun streakWindowStart(lastEpochDay: Long): Long = lastEpochDay - (STREAK_WINDOW_DAYS - 1)

    /** Accepts "," as decimal separator, like weight input in the workout screen. */
    fun parseDecimal(text: String): Double? {
        val trimmed = text.trim()
        if (!PLAIN_DECIMAL.matches(trimmed)) return null
        return trimmed.replace(',', '.').toDoubleOrNull()
    }

    fun parsePortion(text: String): Double? = parseDecimal(text)?.takeIf { it in MIN_PORTION..MAX_PORTION }

    fun parseField(text: String, max: Double): FieldValue {
        if (text.isBlank()) return FieldValue.Blank
        // "2,500" is ambiguous (2.5 or 2500); refuse rather than guess when thousands are plausible.
        if (max >= 1000.0 && THOUSANDS.matches(text.trim())) return FieldValue.Invalid
        val value = parseDecimal(text) ?: return FieldValue.Invalid
        return if (value in 0.0..max) FieldValue.Value(value) else FieldValue.Invalid
    }

    /** Adds `https://` when no scheme is given. Returns null if the result isn't an http(s) link. */
    fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val hasScheme = SCHEME.containsMatchIn(trimmed)
        if (!hasScheme) {
            val colon = trimmed.indexOf(':')
            val slash = trimmed.indexOf('/')
            if (colon >= 0 && (slash < 0 || colon < slash)) return null
        }
        val withScheme = if (hasScheme) {
            val sep = trimmed.indexOf("://")
            trimmed.substring(0, sep).lowercase() + trimmed.substring(sep)
        } else {
            "https://$trimmed" // not UI text
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host ?: return null
        return if (host.contains('.')) withScheme else null
    }

    fun normalizeTag(raw: String): String? = raw.trim().lowercase().takeIf { it.isNotEmpty() }

    fun isValidReminder(timeOfDayMin: Int?, reminderOffsetMin: Int?): Boolean {
        if (timeOfDayMin != null && timeOfDayMin !in 0..1439) return false
        if (reminderOffsetMin == null) return true
        return timeOfDayMin != null && reminderOffsetMin in REMINDER_OFFSETS
    }
}
