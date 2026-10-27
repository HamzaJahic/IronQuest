package com.ironquest.app.ui.food

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.MacroLine
import com.ironquest.app.domain.nutrition.MacroTargets
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.ui.format.Formats
import com.ironquest.app.ui.format.currentLocale
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 45.0 → "45", 67.5 → "67.5", 0.25 → "0.25", 33.333 → "33.33". Locale-independent on purpose: editor
 * text fields are filled from it and parsed back. Display text uses `Formats.decimal` instead.
 * Two decimals so a 0.25× portion or a 12.25 g value round-trips through text fields unchanged.
 */
fun Double.formatAmount(): String {
    if (isNaN()) return "–"
    val hundredths = (this * 100).roundToLong()
    val sign = if (hundredths < 0) "-" else "" // not UI text: editor field value
    val whole = abs(hundredths) / 100
    val fraction = abs(hundredths) % 100
    return when {
        fraction == 0L -> "$sign$whole" // not UI text: editor field value
        fraction % 10 == 0L -> "$sign$whole.${fraction / 10}" // not UI text: editor field value
        else -> "$sign$whole.${fraction.toString().padStart(2, '0')}" // not UI text: editor field value
    }
}

/** A display amount rounded to a whole number, without digit grouping ("1850"): kcal and grams in dense lines. */
internal fun Double.wholeText(locale: Locale): String = Formats.decimal(roundToInt().toDouble(), locale, 0)

/** A display decimal ("67.5" / "67,5"), up to two decimals, without grouping; rounds half up like [formatAmount]. */
internal fun Double.amountText(locale: Locale): String = Formats.decimal((this * 100).roundToLong() / 100.0, locale)

/** "P45 C60 F12" */
@Composable
fun macroLine(macros: Macros): String {
    val locale = currentLocale()
    return stringResource(R.string.food_macro_line, macros.proteinG.amountText(locale), macros.carbsG.amountText(locale), macros.fatG.amountText(locale))
}

/** "35 g protein · 60 g carbs · 8 g fat": spelled out for the meal lists (macroLine stays for dense spots). */
@Composable
fun macroWords(macros: Macros): String {
    val locale = currentLocale()
    return stringResource(R.string.food_macro_words, macros.proteinG.amountText(locale), macros.carbsG.amountText(locale), macros.fatG.amountText(locale))
}

/** "132 / 180 g" or "132 g" without a target. */
@Composable
fun MacroLine.display(unit: String): String {
    val locale = currentLocale()
    val target = target
    return if (target != null) {
        stringResource(R.string.food_progress_of_target, eaten.amountText(locale), target.amountText(locale), unit)
    } else {
        stringResource(R.string.food_progress_eaten, eaten.amountText(locale), unit)
    }
}

/** "P180 C200 F60 · 2500 ml" or "No targets". */
@Composable
fun MacroTargets.summaryLine(): String {
    val locale = currentLocale()
    val parts = listOfNotNull(
        proteinG?.let { stringResource(R.string.food_summary_protein, it.amountText(locale)) },
        carbsG?.let { stringResource(R.string.food_summary_carbs, it.amountText(locale)) },
        fatG?.let { stringResource(R.string.food_summary_fat, it.amountText(locale)) },
        fiberG?.let { stringResource(R.string.food_summary_fiber, it.amountText(locale)) },
        kcal?.let { stringResource(R.string.food_amount_kcal, it.wholeText(locale)) },
        waterMl?.let { stringResource(R.string.food_amount_ml, it.toDouble().wholeText(locale)) },
    )
    return if (parts.isEmpty()) stringResource(R.string.food_no_targets) else parts.joinToString(stringResource(R.string.common_dot_separator))
}
