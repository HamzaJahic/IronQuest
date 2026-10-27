package com.ironquest.app.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UiTextTest {

    @Test
    fun `builders keep the id and the arguments`() {
        assertEquals(UiText.Res(7, listOf("Bench", 3)), uiText(7, "Bench", 3))
        assertEquals(UiText.Plural(9, 2, listOf(2)), uiPlural(9, 2))
        assertEquals(UiText.Plural(9, 2, listOf(2, "Water")), uiPlural(9, 2, 2, "Water"))
    }

    @Test
    fun `equal ids with different arguments are different texts`() {
        assertNotEquals(uiText(7, "Bench"), uiText(7, "Squat"))
    }
}
