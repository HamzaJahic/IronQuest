package com.ironquest.app.ui.food

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FoodFormatTest {

    @Test
    fun `amounts keep up to two decimals without trailing zeros`() {
        assertEquals("45", 45.0.formatAmount())
        assertEquals("67.5", 67.5.formatAmount())
        assertEquals("33.33", 33.333.formatAmount())
        assertEquals("0.25", 0.25.formatAmount())
        assertEquals("0", 0.0.formatAmount())
        assertEquals("-0.5", (-0.5).formatAmount())
    }

    @Test
    fun `formatting ignores the default locale and rounds up cleanly`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("12.25", 12.25.formatAmount())
        } finally {
            Locale.setDefault(previous)
        }
        assertEquals("1", 0.999.formatAmount())
    }
}
