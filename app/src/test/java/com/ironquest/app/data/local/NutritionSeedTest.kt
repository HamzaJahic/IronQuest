package com.ironquest.app.data.local

import com.ironquest.app.fakes.EnglishText
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionSeedTest {

    @Test
    fun `the resource-based seed equals the frozen English seed`() {
        val seeded = NutritionSeed.meals(EnglishText::string)
        assertEquals(SeedGolden.meals.size, seeded.size)
        seeded.zip(SeedGolden.meals).forEach { (actual, golden) ->
            assertEquals(golden.name, actual.name)
            assertEquals(golden.proteinG, actual.proteinG, 0.0)
            assertEquals(golden.carbsG, actual.carbsG, 0.0)
            assertEquals(golden.fatG, actual.fatG, 0.0)
            assertEquals(golden.fiber, actual.fiberG, 0.0)
            assertEquals(golden.name, golden.tags, actual.tags)
        }
    }
}
