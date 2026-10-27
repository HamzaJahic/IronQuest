package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class MacrosTest {

    private val chickenRice = Macros(proteinG = 45.0, carbsG = 60.0, fatG = 12.0, fiberG = 6.0)

    @Test
    fun `kcal is derived 4-4-9 when not entered`() {
        assertEquals(528.0, chickenRice.effectiveKcal, 0.0)
    }

    @Test
    fun `entered kcal wins over the derived value`() {
        assertEquals(530.0, chickenRice.copy(kcal = 530.0).effectiveKcal, 0.0)
    }

    @Test
    fun `portion scales every field and resolves kcal`() {
        val scaled = chickenRice * 1.5
        assertEquals(67.5, scaled.proteinG, 1e-9)
        assertEquals(90.0, scaled.carbsG, 1e-9)
        assertEquals(18.0, scaled.fatG, 1e-9)
        assertEquals(9.0, scaled.fiberG, 1e-9)
        assertEquals(792.0, checkNotNull(scaled.kcal), 1e-9)
    }

    @Test
    fun `plus sums fields and effective kcal`() {
        val total = Macros.ZERO + chickenRice + chickenRice.copy(kcal = 500.0)
        assertEquals(90.0, total.proteinG, 1e-9)
        assertEquals(1028.0, total.effectiveKcal, 1e-9)
    }
}
