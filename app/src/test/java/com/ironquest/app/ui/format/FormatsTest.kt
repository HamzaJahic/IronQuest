package com.ironquest.app.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FormatsTest {

    private val english = Locale.US
    private val german = Locale.GERMANY

    @Test
    fun `integers group by locale`() {
        assertEquals("1,250", Formats.integer(1250, english))
        assertEquals("1.250", Formats.integer(1250, german))
    }

    @Test
    fun `decimals drop trailing zeros and use the locale separator`() {
        assertEquals("82.5", Formats.decimal(82.5, english))
        assertEquals("82,5", Formats.decimal(82.5, german))
        assertEquals("100", Formats.decimal(100.0, english))
        assertEquals("0.25", Formats.decimal(0.25, english))
        assertEquals("1.33", Formats.decimal(4.0 / 3, english))
    }

    @Test
    fun `time of day follows the 24 hour setting`() {
        assertEquals("07:30", Formats.timeOfDay(7 * 60 + 30, english, is24Hour = true))
        assertEquals("7:30 AM", Formats.timeOfDay(7 * 60 + 30, english, is24Hour = false).replace('\u202f', ' '))
    }

    @Test
    fun `time of day is zero padded in 24 hour mode`() {
        assertEquals("07:30", Formats.timeOfDay(450, Locale.US, is24Hour = true))
        assertEquals("00:05", Formats.timeOfDay(5, Locale.US, is24Hour = true))
        assertEquals("23:59", Formats.timeOfDay(1439, Locale.US, is24Hour = true))
    }

    @Test
    fun `dates format with a pattern in the given locale`() {
        val date = LocalDate.of(2026, 10, 9)
        assertEquals("Friday, 9 October", Formats.date(date, "EEEE, d MMMM", english))
        assertEquals("Freitag, 9. Oktober", Formats.date(date, "EEEE, d. MMMM", german))
    }
}
