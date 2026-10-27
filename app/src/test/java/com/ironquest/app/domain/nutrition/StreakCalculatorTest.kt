package com.ironquest.app.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    @Test
    fun `run ending today counts`() = assertEquals(3, StreakCalculator.currentStreak(setOf(8L, 9L, 10L), today = 10))

    @Test
    fun `run ending yesterday still counts when today is not taken yet`() = assertEquals(3, StreakCalculator.currentStreak(setOf(8L, 9L, 10L), today = 11))

    @Test
    fun `a gap breaks the streak`() = assertEquals(1, StreakCalculator.currentStreak(setOf(8L, 10L), today = 10))

    @Test
    fun `two days ago is no streak`() = assertEquals(0, StreakCalculator.currentStreak(setOf(9L), today = 11))

    @Test
    fun `empty is zero`() = assertEquals(0, StreakCalculator.currentStreak(emptySet(), today = 10))
}
