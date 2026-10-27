package com.ironquest.app.ui.food

import com.ironquest.app.domain.food.FoodChoice
import com.ironquest.app.domain.food.FoodSource
import com.ironquest.app.domain.food.FoodUnit
import com.ironquest.app.domain.food.UnitKind
import com.ironquest.app.domain.nutrition.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngredientCalculatorTest {

    private val egg = FoodUnit(UnitKind.PIECE, 50.0)

    private val chicken = FoodChoice("b:1", "Chicken, breast, raw", null, Macros(22.5, 0.0, 1.93, 0.0, 112.0), listOf(FoodUnit.GRAM), FoodSource.BUILT_IN)

    /** No energy value, so kcal is derived: 2.69·4 + 28.2·4 + 0.28·9 = 126.08 per 100 g. */
    private val rice = FoodChoice("b:2", "Rice, white, cooked", null, Macros(2.69, 28.2, 0.28, 0.4, null), listOf(FoodUnit.GRAM), FoodSource.BUILT_IN)

    private val eggs = FoodChoice("b:3", "Egg, whole", null, Macros(12.0, 1.0, 10.0, 0.0, 140.0), listOf(FoodUnit.GRAM, egg), FoodSource.BUILT_IN)

    private fun row(key: Long, food: FoodChoice, text: String, unit: FoodUnit = FoodUnit.GRAM) = IngredientRow(key, food, unit, text)

    @Test
    fun `a gram row scales the per-100 g values by its amount`() {
        val macros = checkNotNull(row(0, chicken, "150").macros)
        assertEquals(33.75, macros.proteinG, 1e-9)
        assertEquals(2.895, macros.fatG, 1e-9)
        assertEquals(168.0, checkNotNull(macros.kcal), 1e-9)
        assertNull(row(1, chicken, "abc").macros)
    }

    @Test
    fun `amounts accept comma decimals and reject zero, blank, junk and more than the unit allows`() {
        assertEquals(12.5, checkNotNull(row(0, chicken, " 12,5 ").amount), 0.0)
        listOf("0", "", "abc", "5000.5", "-5").forEach { text -> assertNull(text, row(0, chicken, text).amount) }
        assertNull(row(0, eggs, "101", egg).amount)
    }

    @Test
    fun `a piece row counts pieces through the unit weight`() {
        val macros = checkNotNull(row(0, eggs, "3", egg).macros)
        assertEquals(18.0, macros.proteinG, 1e-9)
        assertEquals(210.0, checkNotNull(macros.kcal), 1e-9)
    }

    @Test
    fun `total sums all rows and is null when empty or any row is invalid`() {
        val total = checkNotNull(IngredientCalculator.totalOf(listOf(row(0, chicken, "200"), row(1, rice, "150"))))
        assertEquals(49.035, total.proteinG, 1e-9)
        assertEquals(42.3, total.carbsG, 1e-9)
        assertEquals(4.28, total.fatG, 1e-9)
        assertEquals(0.6, total.fiberG, 1e-9)
        assertNull(IngredientCalculator.totalOf(emptyList()))
        assertNull(IngredientCalculator.totalOf(listOf(row(0, chicken, "200"), row(1, rice, ""))))
    }

    @Test
    fun `total kcal is blank only when no ingredient has an energy value`() {
        assertNull(checkNotNull(IngredientCalculator.totalOf(listOf(row(0, rice, "150")))).kcal)
        val mixed = checkNotNull(IngredientCalculator.totalOf(listOf(row(0, chicken, "200"), row(1, rice, "150"))))
        assertEquals(224.0 + 126.08 * 1.5, checkNotNull(mixed.kcal), 1e-9)
    }

    @Test
    fun `apply writes rounded totals into the macro fields and keeps everything else`() {
        val form = MealForm(name = "Bowl", protein = "1", kcal = "999", tags = "lunch", recipeText = "Mix.")
        val applied = IngredientCalculator.applyTo(form, Macros(49.035, 42.300000000000004, 4.28, 0.6000000000000001, 413.12))
        assertEquals(
            form.copy(protein = "49", carbs = "42.3", fat = "4.3", fiber = "0.6", kcal = "413"),
            applied,
        )
        assertEquals("", IngredientCalculator.applyTo(form, Macros(10.0, 0.0, 0.0, 0.0, null)).kcal)
    }
}
