package com.ironquest.app.ui.format

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware numbers and dates for display. File formats (CSV, backup) never use these. */
object Formats {

    /** 1250 → "1,250" (en) / "1.250" (de). */
    fun integer(value: Number, locale: Locale): String = NumberFormat.getIntegerInstance(locale).format(value)

    /**
     * At most [maxDecimals] decimals, no trailing zeros: 82.5 → "82.5" / "82,5". No digit grouping,
     * so a value that goes back into a text field (1250.5) reads the same way it is typed.
     */
    fun decimal(value: Double, locale: Locale, maxDecimals: Int = 2): String = NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = maxDecimals
    }.format(value)

    /** Minutes after midnight as a time of day: "07:30" with [is24Hour], else the locale's short time ("7:30 AM"). */
    fun timeOfDay(minutes: Int, locale: Locale, is24Hour: Boolean): String {
        val time = LocalTime.of(minutes / 60, minutes % 60)
        val formatter = if (is24Hour) {
            DateTimeFormatter.ofPattern("HH:mm", locale) // not UI text: 24-hour pattern for the DateTimeFormatter
        } else {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        }
        return time.format(formatter)
    }

    /** [date] with an explicit [pattern]; screens get the pattern from [DatePatterns] (locale-ordered). */
    fun date(date: LocalDate, pattern: String, locale: Locale): String = date.format(DateTimeFormatter.ofPattern(pattern, locale))
}
