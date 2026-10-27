package com.ironquest.app.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import java.time.LocalDate
import java.util.Locale

/**
 * Date skeletons ("EEEEdMMMM") turned into the locale's own order and punctuation
 * ("EEEE, d MMMM" in en-GB, "EEEE, MMMM d" in en-US).
 */
object DatePatterns {
    const val WEEKDAY_DAY_MONTH = "EEEEdMMMM" // not UI text: skeleton for android.text.format.DateFormat
    const val DAY_MONTH = "dMMM" // not UI text: skeleton for android.text.format.DateFormat
    const val WEEKDAY = "EEEE" // not UI text: skeleton for android.text.format.DateFormat
    const val MONTH = "MMMM" // not UI text: skeleton for android.text.format.DateFormat
    const val MONTH_YEAR = "MMMMyyyy" // not UI text: skeleton for android.text.format.DateFormat
    const val WEEKDAY_DAY_MONTH_YEAR = "EEEEdMMMMyyyy" // not UI text: skeleton for android.text.format.DateFormat

    fun pattern(skeleton: String, locale: Locale): String = DateFormat.getBestDateTimePattern(locale, skeleton)
}

@Composable
fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault() }
}

/** [date] by [skeleton] in the current locale. */
@Composable
fun formatDate(date: LocalDate, skeleton: String): String {
    val locale = currentLocale()
    val pattern = remember(skeleton, locale) { DatePatterns.pattern(skeleton, locale) }
    return Formats.date(date, pattern, locale)
}

@Composable
fun formatTimeOfDay(minutes: Int): String {
    val context = LocalContext.current
    return Formats.timeOfDay(minutes, currentLocale(), DateFormat.is24HourFormat(context))
}

fun formatTimeOfDay(minutes: Int, context: Context): String = Formats.timeOfDay(minutes, Locale.getDefault(), DateFormat.is24HourFormat(context))
