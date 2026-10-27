package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FoodAmountTest {
    private val egg = Macros(proteinG = 12.6, carbsG = 0.7, fatG = 9.5, kcal = 143.0)
    private val piece = FoodUnit(UnitKind.PIECE, gramsPerUnit = 50.0)
    private val cup = FoodUnit(UnitKind.PORTION, gramsPerUnit = 158.0, label = "cup")

    @Test
    fun `grams and pieces parse within their limits`() {
        assertEquals(200.0, checkNotNull(FoodAmount.parse("200", FoodUnit.GRAM)), 0.0)
        assertEquals(1.5, checkNotNull(FoodAmount.parse("1,5", piece)), 0.0)
        assertNull(FoodAmount.parse("0", FoodUnit.GRAM))
        assertNull(FoodAmount.parse("5001", FoodUnit.GRAM))
        assertNull(FoodAmount.parse("101", piece))
        assertNull(FoodAmount.parse("", piece))
        assertNull(FoodAmount.parse("abc", FoodUnit.GRAM))
    }

    @Test
    fun `macros scale with grams of the unit`() {
        val three = FoodAmount.macrosFor(egg, amount = 3.0, unit = piece)
        assertEquals(18.9, three.proteinG, 1e-9)
        assertEquals(214.5, checkNotNull(three.kcal), 1e-9)
        assertEquals(150.0, FoodAmount.grams(3.0, piece), 0.0)
        assertEquals(79.0, FoodAmount.grams(0.5, cup), 0.0)
    }

    @Test
    fun `units round-trip through their storage key`() {
        listOf(FoodUnit.GRAM, FoodUnit.MILLILITRE, piece, FoodUnit(UnitKind.SERVING, 30.0), cup, FoodUnit.MEAL_PORTION).forEach { unit ->
            assertEquals(unit, FoodUnit.fromStorage(unit.storageKey, unit.gramsPerUnit))
        }
        assertEquals(FoodUnit.GRAM, FoodUnit.fromStorage("unknown-key", gramsPerUnit = null))
    }
}
