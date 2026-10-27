package com.ironquest.app.domain.workout

import com.ironquest.app.domain.progression.formatKg
import java.math.RoundingMode

/** Text rules of the weight and reps/minutes inputs of a live workout. */
object SetInput {
    const val MAX_WEIGHT_KG = 1_000.0
    const val MAX_AMOUNT = 999

    /** Weight stepper step when the exercise has no overload increment. */
    const val FALLBACK_STEP_KG = 2.5

    private const val MAX_WEIGHT_CHARS = 6
    private const val MAX_AMOUNT_DIGITS = 3

    /**
     * [text] as a weight input, "," accepted as decimal separator (common on European keyboards);
     * null when it can't become a weight, so the caller keeps the previous text.
     */
    fun cleanWeight(text: String): String? {
        val cleaned = text.replace(',', '.').filter { char -> char.isDigit() || char == '.' }
        val separators = cleaned.count { char -> char == '.' }
        return cleaned.takeIf { separators <= 1 && it.length <= MAX_WEIGHT_CHARS }
    }

    fun cleanAmount(text: String): String = text.filter { char -> char.isDigit() }.take(MAX_AMOUNT_DIGITS)

    /** [text] ± [stepKg], kept within 0…[MAX_WEIGHT_KG]; empty or unparsable counts as 0. */
    fun stepWeight(text: String, stepKg: Double, up: Boolean): String {
        val current = text.toDoubleOrNull() ?: 0.0
        val next = (if (up) current + stepKg else current - stepKg).coerceIn(0.0, MAX_WEIGHT_KG)
        // Strip FP noise (0.1 + 0.2) so the number reads cleanly.
        return next.toBigDecimal().setScale(3, RoundingMode.HALF_UP).toDouble().formatKg()
    }

    /** Reps/minutes ± 1 within 1…[MAX_AMOUNT]; an empty field jumps to [start] first. */
    fun stepAmount(text: String, up: Boolean, start: Int): String {
        val current = text.toIntOrNull() ?: return start.coerceIn(1, MAX_AMOUNT).toString()
        return (if (up) current + 1 else current - 1).coerceIn(1, MAX_AMOUNT).toString()
    }
}
