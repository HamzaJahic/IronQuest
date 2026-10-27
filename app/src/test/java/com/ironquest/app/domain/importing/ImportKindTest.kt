package com.ironquest.app.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportKindTest {
    @Test
    fun `kinds are grouped into the two CSV families`() {
        assertEquals(
            listOf(ImportKind.PROGRAM, ImportKind.WORKOUT_DAY, ImportKind.WORKOUT_DAYS, ImportKind.EXERCISE, ImportKind.EXERCISES),
            ImportKind.PROGRAM.siblings,
        )
        assertEquals(listOf(ImportKind.MEAL, ImportKind.MEALS, ImportKind.MEAL_PLAN), ImportKind.MEAL.siblings)
        assertEquals(setOf(ImportKind.EXERCISE, ImportKind.EXERCISES), ImportKind.entries.filter { it.isExerciseList }.toSet())
    }

    @Test
    fun `every kind has an example with a header row`() {
        ImportKind.entries.forEach { kind ->
            assertTrue("${kind.name}: example", kind.example.lines().size >= 2)
        }
    }
}
