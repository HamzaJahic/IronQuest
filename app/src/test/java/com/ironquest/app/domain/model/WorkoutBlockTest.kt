package com.ironquest.app.domain.model

import com.ironquest.app.domain.model.SupersetRounds.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutBlockTest {

    private fun ex(id: String, sets: Int = 3, group: String? = null) = TemplateExercise(ExerciseDefinition(id, id, StatType.STRENGTH), sets, RepRange(8, 12), group)

    @Test
    fun `consecutive exercises with the same key form lettered supersets`() {
        val blocks = WorkoutBlock.of(
            listOf(ex("bench"), ex("incline", group = "a"), ex("row", group = "a"), ex("curl", group = "x"), ex("pushdown", group = "x"), ex("lone", group = "z")),
        )
        assertEquals(4, blocks.size)
        assertEquals(WorkoutBlock.Single(ex("bench")), blocks[0])
        val superset = blocks[1] as WorkoutBlock.Superset
        assertEquals("A", superset.letter)
        assertEquals(listOf("incline", "row"), superset.exercises.map { it.exercise.id })
        assertEquals("B", (blocks[2] as WorkoutBlock.Superset).letter)
        // A one-member group is shown as a single exercise.
        assertEquals("lone", (blocks[3] as WorkoutBlock.Single).exercise.exercise.id)
    }

    @Test
    fun `rounds alternate members and skip a shorter member in later rounds`() {
        // A1 has 3 sets, A2 has 2: A1 r1, A2 r1, A1 r2, A2 r2, A1 r3.
        assertEquals(
            listOf(Step(0, 0), Step(1, 0), Step(0, 1), Step(1, 1), Step(0, 2)),
            SupersetRounds.order(listOf(3, 2)),
        )
        assertEquals(3, SupersetRounds.rounds(listOf(3, 2)))
        // Tri-set where the middle one is shortest.
        assertEquals(
            listOf(Step(0, 0), Step(1, 0), Step(2, 0), Step(0, 1), Step(2, 1)),
            SupersetRounds.order(listOf(2, 1, 2)),
        )
    }

    @Test
    fun `next and after follow the round order and skip logged steps`() {
        val counts = listOf(3, 2)
        val logged = mutableSetOf<Step>()
        val visited = mutableListOf<Step>()
        while (true) {
            val step = SupersetRounds.next(counts) { it in logged } ?: break
            visited += step
            logged += step
        }
        assertEquals(SupersetRounds.order(counts), visited)

        // "Log, then …": after A1 r2 comes A2 r2; after the last step there is nothing.
        assertEquals(Step(1, 1), SupersetRounds.after(counts, Step(0, 1)) { false })
        assertNull(SupersetRounds.after(counts, Step(0, 2)) { false })
        // An already-logged partner set (edited out of order) is skipped.
        assertEquals(Step(0, 2), SupersetRounds.after(counts, Step(0, 1)) { it == Step(1, 1) })
    }
}
