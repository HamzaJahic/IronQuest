package com.ironquest.app.res

import com.ironquest.app.R
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.text.uiPlural
import org.junit.Assert.assertEquals
import org.junit.Test

/** The English plural rule in [EnglishText]: `one` for exactly 1, `other` for everything else. */
class EnglishPluralsTest {

    @Test
    fun `plurals resolve to the one form for 1 and the other form for 5`() {
        assertEquals("1 day", EnglishText.resolve(uiPlural(R.plurals.food_day_count, 1)))
        assertEquals("5 days", EnglishText.resolve(uiPlural(R.plurals.food_day_count, 5)))
        assertEquals("1 workout", EnglishText.resolve(uiPlural(R.plurals.you_workouts_count, 1)))
        assertEquals("5 workouts", EnglishText.resolve(uiPlural(R.plurals.you_workouts_count, 5)))
        assertEquals("1 rep", EnglishText.resolve(uiPlural(R.plurals.workout_set_reps_count, 1)))
        assertEquals("5 reps", EnglishText.resolve(uiPlural(R.plurals.workout_set_reps_count, 5)))
    }
}
