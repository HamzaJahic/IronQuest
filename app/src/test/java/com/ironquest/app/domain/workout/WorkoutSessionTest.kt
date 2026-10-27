package com.ironquest.app.domain.workout

import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.GhostSession
import com.ironquest.app.domain.model.InProgressSession
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.PersistedSet
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TemplateExercise
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.domain.model.WorkoutBlock
import com.ironquest.app.domain.model.WorkoutTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WorkoutSessionTest {

    private val bench = TemplateExercise(
        ExerciseDefinition("bench", "Bench", StatType.STRENGTH, incrementKg = 2.5),
        targetSets = 3,
        repRange = RepRange(6, 8),
    )
    private val row = TemplateExercise(
        ExerciseDefinition("row", "Row", StatType.STRENGTH, incrementKg = 0.0),
        targetSets = 2,
        repRange = RepRange(8, 12),
        supersetGroup = "a",
    )
    private val curl = TemplateExercise(
        ExerciseDefinition("curl", "Curl", StatType.STRENGTH),
        targetSets = 3,
        repRange = RepRange(10, 12),
        supersetGroup = "a",
    )
    private val bike = TemplateExercise(
        ExerciseDefinition("bike", "Bike", StatType.STAMINA, trackingType = TrackingType.DURATION),
        targetSets = 1,
        repRange = RepRange(20, 30),
    )
    private val template = WorkoutTemplate("U1", "Upper 1", listOf(bench, row, curl, bike))

    private val ghost = GhostSession(
        workoutId = 7,
        date = LocalDate.of(2026, 10, 1),
        setsByExercise = mapOf(
            // All three at the top of 6–8 → Level up: suggestion 102.5.
            "bench" to listOf(LoggedSet(0, 8, 100.0), LoggedSet(1, 8, 100.0), LoggedSet(2, 8, 100.0)),
            "bike" to listOf(LoggedSet(0, 0, 0.0, durationMinutes = 25)),
        ),
    )

    private fun fresh() = WorkoutSession.start(template, ghost = ghost, resumed = null)

    private fun WorkoutSession.bench() = checkNotNull(exercise("bench"))

    @Test
    fun `start builds the prescribed rows with last time's sets as ghosts`() {
        val session = fresh()
        assertEquals(3, session.bench().rows.size)
        assertEquals(LoggedSet(1, 8, 100.0), session.bench().rows[1].ghost)
        assertEquals(9, session.targetSets)
        assertEquals(0, session.loggedSets)
    }

    @Test
    fun `start resumes logged sets, including extra ones beyond the prescription`() {
        val resumed = InProgressSession(
            workoutId = 3,
            templateId = "U1",
            setsByExercise = mapOf(
                "bench" to listOf(
                    PersistedSet(id = 11, set = LoggedSet(0, 8, 102.5), xpAwarded = 15),
                    PersistedSet(id = 12, set = LoggedSet(3, 6, 102.5), xpAwarded = 10),
                ),
            ),
        )
        val session = WorkoutSession.start(template, ghost, resumed)
        assertEquals(4, session.bench().rows.size)
        assertEquals("102.5", session.bench().rows[0].weightInput)
        assertEquals("8", session.bench().rows[0].amountInput)
        assertTrue(session.bench().rows[3].isLogged)
        assertEquals(2, session.loggedSets)
        assertEquals(25, session.earnedXp)
    }

    @Test
    fun `shown weight is the typed weight, else the suggestion, else last time's`() {
        val exercise = fresh().bench()
        val firstRow = exercise.rows[0]
        assertEquals("102.5", exercise.shownWeight(firstRow)) // all sets at top → Level up +2.5
        assertEquals("90", exercise.shownWeight(firstRow.copy(weightInput = "90")))
    }

    @Test
    fun `logging a set carries weight and reps into the open rows below it`() {
        val session = fresh().update("bench") { exercise ->
            exercise.withLoggedSet(LoggedSet(0, 7, 102.5), setId = 1, xp = 10)
        }
        val rows = session.bench().rows
        assertTrue(rows[0].isLogged)
        assertEquals(10, rows[0].xp)
        assertEquals("102.5", rows[1].weightInput)
        assertEquals("7", rows[2].amountInput)
        assertEquals(10, session.earnedXp)
    }

    @Test
    fun `cardio logging carries nothing forward`() {
        val session = fresh().update("bike") { exercise ->
            exercise.withLoggedSet(LoggedSet(0, 0, 0.0, durationMinutes = 30), setId = 1, xp = 30)
        }
        assertEquals(1, checkNotNull(session.exercise("bike")).loggedCount)
    }

    @Test
    fun `added set copies the last row and takes the ghost at its index`() {
        val extraGhost = LoggedSet(3, 6, 100.0)
        val session = fresh()
            .updateRow("bench", 2) { row -> row.copy(weightInput = "100", amountInput = "6") }
            .withAddedSet("bench") { _, setIndex -> extraGhost.takeIf { setIndex == 3 } }
        val added = session.bench().rows.last()
        assertEquals(3, added.index)
        assertEquals("100", added.weightInput)
        assertEquals("6", added.amountInput)
        assertEquals(extraGhost, added.ghost)
    }

    @Test
    fun `added round extends only the longest members of the superset`() {
        val session = fresh().withAddedRound(listOf("row", "curl"), ghost::ghostFor)
        assertEquals(2, checkNotNull(session.exercise("row")).rows.size)
        assertEquals(4, checkNotNull(session.exercise("curl")).rows.size)
    }

    @Test
    fun `deleting a prescribed set reopens it with its values, an extra last set disappears`() {
        // In the app the row's inputs are typed before it's logged.
        val logged = fresh().updateRow("bench", 0) { row -> row.copy(weightInput = "100", amountInput = "8") }.update("bench") { exercise ->
            exercise.withLoggedSet(LoggedSet(0, 8, 100.0), setId = 1, xp = 10)
        }.withAddedSet("bench", ghost::ghostFor).update("bench") { exercise ->
            exercise.withLoggedSet(LoggedSet(3, 5, 100.0), setId = 2, xp = 10)
        }
        val reopened = logged.update("bench") { exercise -> exercise.withoutSet(0) }.bench().rows[0]
        assertFalse(reopened.isLogged)
        assertEquals("8", reopened.amountInput)
        assertNull(reopened.xp)
        assertEquals(3, logged.update("bench") { exercise -> exercise.withoutSet(3) }.bench().rows.size)
    }

    @Test
    fun `weight stepping uses the increment, 2,5 when the exercise has none`() {
        val stepped = fresh().update("bench") { exercise -> exercise.withSteppedWeight(0, up = true) }
        assertEquals("105", stepped.bench().rows[0].weightInput) // from shown 102.5
        val rowStepped = fresh().update("row") { exercise -> exercise.withSteppedWeight(0, up = true) }
        assertEquals("2.5", checkNotNull(rowStepped.exercise("row")).rows[0].weightInput)
    }

    @Test
    fun `amount stepping starts from last time's reps, else the range bottom`() {
        val bench = fresh().update("bench") { exercise -> exercise.withSteppedAmount(0, up = true) }
        assertEquals("8", bench.bench().rows[0].amountInput)
        val row = fresh().update("row") { exercise -> exercise.withSteppedAmount(0, up = true) }
        assertEquals("8", checkNotNull(row.exercise("row")).rows[0].amountInput)
    }

    @Test
    fun `row status follows input and persistence`() {
        val exercise = fresh().bench()
        val empty = exercise.rows[0]
        assertEquals(RowStatus.EMPTY, exercise.statusOf(empty))
        val ready = empty.copy(weightInput = "100", amountInput = "8")
        assertEquals(RowStatus.READY, exercise.statusOf(ready))
        val logged = ready.copy(persistedId = 1, persisted = LoggedSet(0, 8, 100.0))
        assertEquals(RowStatus.LOGGED, exercise.statusOf(logged))
        assertEquals(RowStatus.DIRTY, exercise.statusOf(logged.copy(amountInput = "9")))
    }

    @Test
    fun `superset progress walks rounds and the first unfinished page is resumed`() {
        val session = fresh()
        val group = session.exercisesOf(template.blocks[1])
        assertEquals(listOf("row", "curl"), group.map { it.id })
        val current = checkNotNull(group.supersetCurrent())
        assertEquals(0, current.member)
        assertEquals(1, checkNotNull(group.supersetAfter(current)).member)
        assertEquals(0, session.initialPage(template.blocks))

        val benchDone = session.update("bench") { exercise ->
            (0..2).fold(exercise) { acc, index -> acc.withLoggedSet(LoggedSet(index, 8, 100.0), setId = index + 1L, xp = 10) }
        }
        assertTrue(benchDone.exercisesOf(template.blocks[0]).isBlockDone())
        assertEquals(1, benchDone.initialPage(template.blocks))
        assertTrue(template.blocks[1] is WorkoutBlock.Superset)
    }
}
