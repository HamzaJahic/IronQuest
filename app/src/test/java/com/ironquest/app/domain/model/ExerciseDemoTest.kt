package com.ironquest.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseDemoTest {
    @Test
    fun `the name is searched with form appended and URL-encoded`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=Barbell+Row+exercise+form",
            ExerciseDemo.youtubeSearchUrl("  Barbell Row "),
        )
        assertEquals(
            "https://www.youtube.com/results?search_query=Fly%2C+cable+%22low%22+exercise+form",
            ExerciseDemo.youtubeSearchUrl("Fly, cable \"low\""),
        )
    }
}
