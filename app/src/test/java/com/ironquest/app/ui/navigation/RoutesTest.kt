package com.ironquest.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `tab routes from before the rename map to the new ones`() {
        assertEquals(Routes.TODAY, Routes.fromLegacy("hub"))
        assertEquals(Routes.LOG, Routes.fromLegacy("chronicle"))
        assertEquals(Routes.FOOD, Routes.fromLegacy("rations"))
    }

    @Test
    fun `current and unknown routes pass through unchanged`() {
        assertEquals(Routes.FOOD, Routes.fromLegacy(Routes.FOOD))
        assertEquals(Routes.YOU, Routes.fromLegacy(Routes.YOU))
        assertEquals("something-else", Routes.fromLegacy("something-else"))
    }
}
