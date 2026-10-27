package com.ironquest.app.data.settings

import com.ironquest.app.domain.program.NextWorkoutChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextWorkoutChoiceCodecTest {
    @Test
    fun `missing, blank or wrong-typed values are no choice`() {
        assertNull(NextWorkoutChoiceCodec.decode(null, null))
        assertNull(NextWorkoutChoiceCodec.decode("UPPER_1", null))
        assertNull(NextWorkoutChoiceCodec.decode(null, 5L))
        assertNull(NextWorkoutChoiceCodec.decode(" ", 5L))
        assertNull(NextWorkoutChoiceCodec.decode(42, 5L))
        assertNull(NextWorkoutChoiceCodec.decode("UPPER_1", "5"))
        assertNull(NextWorkoutChoiceCodec.decode("UPPER_1", 5))
    }

    @Test
    fun `a stored choice reads back`() = assertEquals(NextWorkoutChoice("UPPER_1", 5L), NextWorkoutChoiceCodec.decode("UPPER_1", 5L))
}
