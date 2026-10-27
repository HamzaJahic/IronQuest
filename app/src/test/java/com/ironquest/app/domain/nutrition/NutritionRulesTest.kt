package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionRulesTest {

    @Test
    fun `xp numbers match the spec`() {
        assertEquals(5, NutritionRules.MEAL_XP)
        assertEquals(25, NutritionRules.PROTEIN_BONUS_XP)
        assertEquals(10, NutritionRules.WATER_BONUS_XP)
        assertEquals(2, NutritionRules.SUPPLEMENT_XP)
    }

    @Test
    fun `portion accepts comma decimals within bounds`() {
        assertEquals(1.5, checkNotNull(NutritionRules.parsePortion("1,5")), 0.0)
        assertEquals(0.25, checkNotNull(NutritionRules.parsePortion("0.25")), 0.0)
        assertEquals(5.0, checkNotNull(NutritionRules.parsePortion(" 5 ")), 0.0)
        assertNull(NutritionRules.parsePortion("0.2"))
        assertNull(NutritionRules.parsePortion("5.5"))
        assertNull(NutritionRules.parsePortion("abc"))
    }

    @Test
    fun `parseField distinguishes blank, value and invalid`() {
        assertEquals(FieldValue.Blank, NutritionRules.parseField("  ", 1000.0))
        assertEquals(FieldValue.Value(12.5), NutritionRules.parseField("12,5", 1000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("1001", 1000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("-1", 1000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("x", 1000.0))
    }

    @Test
    fun `urls get https and must have a dotted host`() {
        assertEquals("https://budgetbytes.com/x", NutritionRules.normalizeUrl("budgetbytes.com/x"))
        assertEquals("http://a.bc/recipe", NutritionRules.normalizeUrl(" http://a.bc/recipe "))
        assertNull(NutritionRules.normalizeUrl("ftp://a.bc"))
        assertNull(NutritionRules.normalizeUrl("not a url"))
        assertNull(NutritionRules.normalizeUrl("localhost"))
        assertNull(NutritionRules.normalizeUrl(""))
    }

    @Test
    fun `tags are trimmed and lowercased, blanks dropped`() {
        assertEquals("high-protein", NutritionRules.normalizeTag(" High-Protein "))
        assertNull(NutritionRules.normalizeTag("   "))
    }

    @Test
    fun `protein bonus needs the target reached`() {
        val targets = MacroTargets(proteinG = 180.0)
        assertEquals(25, NutritionRules.proteinBonus(180.0, targets))
        assertEquals(0, NutritionRules.proteinBonus(179.9, targets))
        assertEquals(0, NutritionRules.proteinBonus(500.0, MacroTargets()))
        assertEquals(0, NutritionRules.proteinBonus(500.0, null))
    }

    @Test
    fun `water bonus needs the target reached`() {
        val targets = MacroTargets(waterMl = 2500)
        assertEquals(10, NutritionRules.waterBonus(2500, targets))
        assertEquals(0, NutritionRules.waterBonus(2250, targets))
        assertEquals(0, NutritionRules.waterBonus(9000, null))
    }

    @Test
    fun `reminder needs a time and an allowed offset`() {
        assertTrue(NutritionRules.isValidReminder(null, null))
        assertTrue(NutritionRules.isValidReminder(780, null))
        assertTrue(NutritionRules.isValidReminder(780, 15))
        assertFalse(NutritionRules.isValidReminder(null, 15))
        assertFalse(NutritionRules.isValidReminder(780, 20))
        assertFalse(NutritionRules.isValidReminder(1440, null))
    }

    @Test
    fun `url scheme is lowercased and non-http schemes are rejected`() {
        assertEquals("https://Example.com/x", NutritionRules.normalizeUrl("HTTPS://Example.com/x"))
        assertNull(NutritionRules.normalizeUrl("mailto:x@y.com"))
        assertEquals(
            "https://www.budgetbytes.com/a?b=1#c",
            NutritionRules.normalizeUrl("www.budgetbytes.com/a?b=1#c"),
        )
    }

    @Test
    fun `thousands separators are invalid for large fields but decimal commas stay valid`() {
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("2,500", 10000.0))
        assertEquals(FieldValue.Value(12.5), NutritionRules.parseField("12,5", 10000.0))
        assertEquals(FieldValue.Value(2.25), NutritionRules.parseField("2,25", 10000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("2,500", 1000.0))
    }

    @Test
    fun `only plain decimals are parsed`() {
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("1e3", 1000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("1.5f", 1000.0))
        assertEquals(FieldValue.Invalid, NutritionRules.parseField("NaN", 1000.0))
        assertNull(NutritionRules.parseDecimal("-1"))
    }

    @Test
    fun `daily water is clamped between 0 and the daily cap`() {
        assertEquals(10_000, NutritionRules.MAX_WATER_DAY_ML)
        assertEquals(500, NutritionRules.clampWater(250, 250))
        assertEquals(0, NutritionRules.clampWater(250, -500))
        assertEquals(10_000, NutritionRules.clampWater(9_900, 250))
        assertEquals(10_000, NutritionRules.clampWater(10_000, 250))
        // A day already above the cap (written before the cap existed) can still go down.
        assertEquals(10_750, NutritionRules.clampWater(11_000, -250))
    }

    @Test
    fun `intake reads are bounded to a 400-day window ending at the given day`() {
        assertEquals(400, NutritionRules.STREAK_WINDOW_DAYS)
        // 1000..1399 inclusive is exactly 400 days, so a streak can show at most 400.
        assertEquals(1000L, NutritionRules.streakWindowStart(1399L))
    }
}
