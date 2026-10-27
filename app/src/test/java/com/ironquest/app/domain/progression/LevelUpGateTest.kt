package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.model.StatLevelUp
import com.ironquest.app.domain.model.StatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LevelUpGateTest {

    @Test
    fun `no event stays no event`() {
        assertNull(gateLevelUp(null, highestLevelReached = 5))
    }

    @Test
    fun `a character level above the highest reached is reported`() {
        val event = LevelUpEvent(4, 5, emptyList())
        assertEquals(event, gateLevelUp(event, highestLevelReached = 4))
    }

    @Test
    fun `re-reaching a level already reached is not reported`() {
        // Un-tick dropped the character 5 -> 4; re-tick crosses 4 -> 5 again.
        assertNull(gateLevelUp(LevelUpEvent(4, 5, emptyList()), highestLevelReached = 5))
    }

    @Test
    fun `crossing past the highest reached is reported from the real previous level`() {
        val event = LevelUpEvent(3, 6, emptyList())
        assertEquals(event, gateLevelUp(event, highestLevelReached = 5))
    }

    @Test
    fun `stat level-ups survive when the character part is suppressed`() {
        val stats = listOf(StatLevelUp(StatType.STRENGTH, 3))
        val gated = checkNotNull(gateLevelUp(LevelUpEvent(4, 5, stats), highestLevelReached = 5))
        assertFalse(gated.characterLeveledUp)
        assertEquals(stats, gated.statLevelUps)
        assertEquals(5, gated.newLevel)
    }

    @Test
    fun `highest level after an award never goes down`() {
        assertEquals(5, raisedHighestLevel(highestLevelReached = 5, totalXpAfter = 0))
        // 100 + 150 = 250 XP reaches level 3.
        assertEquals(3, raisedHighestLevel(highestLevelReached = 1, totalXpAfter = 250))
    }
}
