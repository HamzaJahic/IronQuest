package com.ironquest.app.ui.food

import com.ironquest.app.domain.nutrition.MacroTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyTargetsFormTest {

    @Test
    fun `all blank is valid and empty`() {
        assertEquals(MacroTargets(), DailyTargetsForm.parse(DailyTargetsTexts()))
    }

    @Test
    fun `numbers parse with comma decimals and water rounds to whole ml`() {
        val parsed = DailyTargetsForm.parse(DailyTargetsTexts(kcal = "2200", protein = "140,5", carbs = "250", fat = "70", water = "3000"))
        assertEquals(MacroTargets(proteinG = 140.5, carbsG = 250.0, fatG = 70.0, kcal = 2200.0, waterMl = 3000), parsed)
    }

    @Test
    fun `text and out of range values are invalid`() {
        assertNull(DailyTargetsForm.parse(DailyTargetsTexts(protein = "abc")))
        assertNull(DailyTargetsForm.parse(DailyTargetsTexts(protein = "2001")))
        assertNull(DailyTargetsForm.parse(DailyTargetsTexts(kcal = "10001")))
        assertNull(DailyTargetsForm.parse(DailyTargetsTexts(water = "10001")))
        assertNull(DailyTargetsForm.parse(DailyTargetsTexts(fat = "-1")))
    }

    @Test
    fun `invalid fields are named`() {
        val texts = DailyTargetsTexts(kcal = "x", protein = "100", water = "99999")
        assertEquals(setOf(TargetField.KCAL, TargetField.WATER), DailyTargetsForm.invalidFields(texts))
    }

    @Test
    fun `existing targets turn into texts and back`() {
        val targets = MacroTargets(proteinG = 140.0, carbsG = 250.5, kcal = 2200.0, waterMl = 3000)
        val texts = DailyTargetsForm.texts(targets)
        assertEquals(DailyTargetsTexts(kcal = "2200", protein = "140", carbs = "250.5", fat = "", water = "3000"), texts)
        assertEquals(targets, DailyTargetsForm.parse(texts))
    }
}
