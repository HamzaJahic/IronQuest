package com.ironquest.app.ui.food.builder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserFoodFormTest {

    @Test
    fun `per 100 g values pass through and blank kcal stays derived`() {
        val draft = checkNotNull(UserFoodForm.toDraft(UserFoodFields(name = " Skyr ", protein = "11", carbs = "4", fat = "0,2"), id = null))
        assertEquals("Skyr", draft.name)
        assertEquals(11.0, draft.per100g.proteinG, 0.0)
        assertEquals(0.2, draft.per100g.fatG, 0.0)
        assertNull(draft.per100g.kcal)
        assertNull(draft.pieceGrams)
    }

    @Test
    fun `per piece converts to per 100 g with the piece weight`() {
        val fields = UserFoodFields(name = "Egg", perPiece = true, protein = "6", fat = "5", kcal = "70", pieceGrams = "50")
        val draft = checkNotNull(UserFoodForm.toDraft(fields, id = 4))
        assertEquals(12.0, draft.per100g.proteinG, 0.0)
        assertEquals(10.0, draft.per100g.fatG, 0.0)
        assertEquals(140.0, checkNotNull(draft.per100g.kcal), 0.0)
        assertEquals(50.0, checkNotNull(draft.pieceGrams), 0.0)
        assertEquals(4L, draft.id)
    }

    @Test
    fun `per piece without a piece weight is refused`() {
        val fields = UserFoodFields(name = "Egg", perPiece = true, protein = "6")
        assertNull(UserFoodForm.toDraft(fields, id = null))
        assertEquals(setOf(UserFoodField.PIECE_GRAMS), UserFoodForm.errors(fields))
    }

    @Test
    fun `blank name is refused`() {
        val fields = UserFoodFields(name = "  ", protein = "5")
        assertNull(UserFoodForm.toDraft(fields, id = null))
        assertEquals(setOf(UserFoodField.NAME), UserFoodForm.errors(fields))
    }

    @Test
    fun `comma decimals are accepted and out of range values are flagged`() {
        assertEquals(2.5, checkNotNull(UserFoodForm.toDraft(UserFoodFields(name = "A", protein = "2,5"), id = null)).per100g.proteinG, 0.0)
        val bad = UserFoodFields(name = "A", protein = "1001", kcal = "10001", carbs = "x")
        assertEquals(setOf(UserFoodField.PROTEIN, UserFoodField.KCAL, UserFoodField.CARBS), UserFoodForm.errors(bad))
    }

    @Test
    fun `an optional piece weight must still be valid when typed`() {
        assertEquals(setOf(UserFoodField.PIECE_GRAMS), UserFoodForm.errors(UserFoodFields(name = "A", pieceGrams = "0")))
    }
}
