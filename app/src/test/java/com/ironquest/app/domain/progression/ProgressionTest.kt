package com.ironquest.app.domain.progression

import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.StatLevelUp
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.domain.progression.ProgressiveOverloadRules
import com.ironquest.app.domain.progression.formatKg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionTest {

    private val curve = LevelCurve.CHARACTER

    @Test
    fun `level curve thresholds`() {
        assertEquals(0L, curve.totalXpForLevel(1))
        assertEquals(100L, curve.totalXpForLevel(2))
        assertEquals(250L, curve.totalXpForLevel(3))
        assertEquals(450L, curve.totalXpForLevel(4))
    }

    @Test
    fun `progressFor agrees with totalXpForLevel at and around every threshold`() {
        for (level in 1..60) {
            val threshold = curve.totalXpForLevel(level)
            assertEquals(level, curve.progressFor(threshold).level)
            assertEquals(0, curve.progressFor(threshold).xpIntoLevel)
            if (threshold > 0) assertEquals(level - 1, curve.progressFor(threshold - 1).level)
        }
    }

    @Test
    fun `progress fraction`() {
        val progress = curve.progressFor(175) // level 2 starts at 100, needs 150
        assertEquals(2, progress.level)
        assertEquals(75, progress.xpIntoLevel)
        assertEquals(0.5f, progress.fraction, 0.0001f)
    }

    @Test
    fun `set xp and ghost slayer bonus`() {
        val ghost = LoggedSet(0, reps = 6, weightKg = 100.0)
        assertEquals(10, XpRules.forSet(LoggedSet(0, 6, 100.0), ghost, TrackingType.WEIGHT_REPS).total)
        assertEquals(15, XpRules.forSet(LoggedSet(0, 7, 100.0), ghost, TrackingType.WEIGHT_REPS).total)
        assertEquals(15, XpRules.forSet(LoggedSet(0, 6, 102.5), ghost, TrackingType.WEIGHT_REPS).total)
        assertEquals(10, XpRules.forSet(LoggedSet(0, 6, 100.0), null, TrackingType.WEIGHT_REPS).total)
    }

    @Test
    fun `lighter weight for more reps does not slay the ghost`() {
        val ghost = LoggedSet(0, reps = 6, weightKg = 100.0)
        assertFalse(XpRules.beatsGhost(LoggedSet(0, 12, 60.0), ghost, TrackingType.WEIGHT_REPS))
    }

    @Test
    fun `cardio xp is per minute with a floor`() {
        assertEquals(10, XpRules.forSet(LoggedSet(0, 0, 0.0, durationMinutes = 5), null, TrackingType.DURATION).base)
        assertEquals(20, XpRules.forSet(LoggedSet(0, 0, 0.0, durationMinutes = 20), null, TrackingType.DURATION).base)
        val ghost = LoggedSet(0, 0, 0.0, durationMinutes = 15)
        assertTrue(XpRules.beatsGhost(LoggedSet(0, 0, 0.0, durationMinutes = 16), ghost, TrackingType.DURATION))
    }

    @Test
    fun `rotation cycles through the 4 day split`() {
        assertEquals("UPPER_1", ProgramCatalog.nextInRotation(null).id)
        assertEquals("LOWER_1", ProgramCatalog.nextInRotation("UPPER_1").id)
        assertEquals("UPPER_2", ProgramCatalog.nextInRotation("LOWER_1").id)
        assertEquals("LOWER_2", ProgramCatalog.nextInRotation("UPPER_2").id)
        assertEquals("UPPER_1", ProgramCatalog.nextInRotation("LOWER_2").id)
    }

    @Test
    fun `catalog exercise ids are unique`() {
        val ids = ProgramCatalog.Exercises.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ProgramCatalog.templates.flatMap { it.exercises }.forEach {
            assertTrue(it.exercise.id in ids)
        }
    }

    @Test
    fun `level up detection covers character and stat thresholds`() {
        fun snap(total: Long, str: Long) = XpSnapshot(total, mapOf(StatType.STRENGTH to str))
        assertEquals(null, detectLevelUp(snap(50, 10), snap(60, 20)))

        val characterUp = checkNotNull(detectLevelUp(snap(95, 0), snap(105, 10)))
        assertEquals(1, characterUp.previousLevel)
        assertEquals(2, characterUp.newLevel)
        assertTrue(characterUp.characterLeveledUp)

        // Stat curve: level 2 at 40 XP.
        val statUp = checkNotNull(detectLevelUp(snap(35, 35), snap(45, 45)))
        assertFalse(statUp.characterLeveledUp)
        assertEquals(listOf(StatLevelUp(StatType.STRENGTH, 2)), statUp.statLevelUps)
    }

    @Test
    fun `kg formatting`() {
        assertEquals("100", 100.0.formatKg())
        assertEquals("102.5", 102.5.formatKg())
        assertEquals("56.25", 56.25.formatKg())
    }
}
