package com.ironquest.app.domain.program

import com.ironquest.app.domain.model.Program
import com.ironquest.app.domain.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextWorkoutTest {
    private val builtIn = Program(null, "", isActive = true, templates = ProgramCatalog.templates)
    private val imported = Program(7, "Push pull", isActive = true, templates = listOf(WorkoutTemplate(Program.customTemplateId(1), "Push", ProgramCatalog.templates.first().exercises)))
    private val chosenAt = 1_000L

    private fun pick(
        program: Program? = builtIn,
        lastId: String? = null,
        lastAt: Long? = null,
        inProgress: String? = null,
        choice: NextWorkoutChoice? = null,
    ) = NextWorkout.pick(program, lastId, lastAt, inProgress, choice)

    @Test
    fun `without a choice the rotation decides`() {
        assertEquals(NextPick("LOWER_1", chosen = false, rotationTemplateId = "LOWER_1"), pick(lastId = "UPPER_1", lastAt = 500))
        assertEquals("UPPER_1", pick().templateId)
    }

    @Test
    fun `a choice wins over the rotation`() {
        val result = pick(lastId = "LOWER_1", lastAt = 500, choice = NextWorkoutChoice("UPPER_1", chosenAt))
        assertEquals(NextPick("UPPER_1", chosen = true, rotationTemplateId = "UPPER_2"), result)
    }

    @Test
    fun `a completion after the choice ends it and the rotation continues from what was done`() {
        val result = pick(lastId = "UPPER_1", lastAt = chosenAt + 1, choice = NextWorkoutChoice("UPPER_1", chosenAt))
        assertEquals(NextPick("LOWER_1", chosen = false, rotationTemplateId = "LOWER_1"), result)
    }

    @Test
    fun `a completion at or before the choice keeps it`() {
        assertTrue(pick(lastId = "LOWER_1", lastAt = chosenAt, choice = NextWorkoutChoice("UPPER_1", chosenAt)).chosen)
        assertTrue(pick(lastId = "LOWER_1", lastAt = chosenAt - 1, choice = NextWorkoutChoice("UPPER_1", chosenAt)).chosen)
    }

    @Test
    fun `a day that is not in the active program is ignored`() {
        assertEquals("UPPER_1", pick(choice = NextWorkoutChoice("C99", chosenAt)).templateId)
        val otherProgram = pick(program = imported, choice = NextWorkoutChoice("UPPER_2", chosenAt))
        assertEquals(NextPick("C1", chosen = false, rotationTemplateId = "C1"), otherProgram)
    }

    @Test
    fun `an unfinished workout wins over the choice`() {
        val result = pick(inProgress = "LOWER_2", choice = NextWorkoutChoice("UPPER_2", chosenAt))
        assertEquals(NextPick("LOWER_2", chosen = false, rotationTemplateId = "UPPER_1"), result)
    }

    @Test
    fun `choosing the rotation's own day is not a choice`() {
        assertFalse(pick(lastId = "UPPER_1", lastAt = 500, choice = NextWorkoutChoice("LOWER_1", chosenAt)).chosen)
    }

    @Test
    fun `no program and nothing unfinished picks nothing`() {
        assertEquals(NextPick(null, chosen = false, rotationTemplateId = null), pick(program = null, choice = NextWorkoutChoice("UPPER_1", chosenAt)))
    }
}
