package com.ironquest.app.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetInputTest {

    @Test
    fun `weight accepts a comma as decimal separator and drops other characters`() {
        assertEquals("82.5", SetInput.cleanWeight("82,5"))
        assertEquals("100", SetInput.cleanWeight("1 00kg"))
    }

    @Test
    fun `weight with two separators or more than six characters is rejected`() {
        assertNull(SetInput.cleanWeight("1.2.3"))
        assertNull(SetInput.cleanWeight("1000.25"))
    }

    @Test
    fun `amount keeps at most three digits`() {
        assertEquals("123", SetInput.cleanAmount("12a34"))
    }

    @Test
    fun `weight steps by the given amount and stays within 0 and 1000`() {
        assertEquals("102.5", SetInput.stepWeight("100", stepKg = 2.5, up = true))
        assertEquals("0", SetInput.stepWeight("1", stepKg = 2.5, up = false))
        assertEquals("1000", SetInput.stepWeight("999", stepKg = 5.0, up = true))
        assertEquals("2.5", SetInput.stepWeight("", stepKg = 2.5, up = true))
    }

    @Test
    fun `weight step strips floating point noise`() {
        assertEquals("0.3", SetInput.stepWeight("0.1", stepKg = 0.2, up = true))
    }

    @Test
    fun `amount steps by one within 1 and 999, an empty field jumps to the start value`() {
        assertEquals("9", SetInput.stepAmount("8", up = true, start = 5))
        assertEquals("1", SetInput.stepAmount("1", up = false, start = 5))
        assertEquals("999", SetInput.stepAmount("999", up = true, start = 5))
        assertEquals("5", SetInput.stepAmount("", up = true, start = 5))
    }
}
