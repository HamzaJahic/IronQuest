package com.ironquest.app.ui.workout

import com.ironquest.app.R
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.LoggedSet
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.domain.model.TrackingType
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import com.ironquest.app.ui.workout.WorkoutLogEditorViewModel.DraftResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WorkoutLogEditorViewModelTest {

    private val bench = LogExerciseUi(ExerciseDefinition("bench", "Bench", StatType.STRENGTH), RepRange(5, 8), emptyList(), emptyList())
    private val bike = LogExerciseUi(
        ExerciseDefinition("bike", "Bike", StatType.STAMINA, TrackingType.DURATION, 0.0),
        RepRange(10, 20),
        emptyList(),
        emptyList(),
    )
    private val day = LocalDate.of(2026, 10, 9)

    @Test
    fun `blank rows are ignored, an empty weight is bodyweight`() {
        assertEquals(LogRow.Blank, WorkoutLogEditorViewModel.parseRow(false, LogSetUi()))
        val set = (WorkoutLogEditorViewModel.parseRow(false, LogSetUi(weight = "", amount = "12")) as LogRow.Ok).set
        assertEquals(0.0, set.weightKg, 0.0)
        assertEquals(12, set.reps)
    }

    @Test
    fun `reps are required and weight is capped`() {
        assertEquals(LogRow.Invalid, WorkoutLogEditorViewModel.parseRow(false, LogSetUi(weight = "80", amount = "")))
        assertEquals(LogRow.Invalid, WorkoutLogEditorViewModel.parseRow(false, LogSetUi(weight = "1001", amount = "5")))
        assertEquals(LogRow.Invalid, WorkoutLogEditorViewModel.parseRow(false, LogSetUi(weight = "80", amount = "0")))
    }

    @Test
    fun `cardio rows are minutes only`() {
        val set = (WorkoutLogEditorViewModel.parseRow(true, LogSetUi(amount = "25")) as LogRow.Ok).set
        assertEquals(25, set.durationMinutes)
        assertEquals(0, set.reps)
    }

    @Test
    fun `a draft keeps filled sets in order and drops empty exercises`() {
        val form = listOf(
            bench.copy(sets = listOf(LogSetUi("80", "8"), LogSetUi(), LogSetUi("77.5", "6"))),
            bike.copy(sets = listOf(LogSetUi())),
        )
        val draft = (WorkoutLogEditorViewModel.toDraft(null, "UPPER_1", day, form) as DraftResult.Ok).draft
        assertEquals(1, draft.exercises.size)
        assertEquals(listOf(80.0, 77.5), draft.exercises.single().sets.map { it.weightKg })
        assertEquals(day, draft.date)
    }

    @Test
    fun `an unreadable row names the set, and an empty form is refused`() {
        val bad = WorkoutLogEditorViewModel.toDraft(null, "UPPER_1", day, listOf(bench.copy(sets = listOf(LogSetUi("80", "8"), LogSetUi("80", "")))))
        assertEquals(uiText(R.string.workout_log_invalid_set_reps, 2, UiText.Verbatim("Bench")), (bad as DraftResult.Invalid).text)
        val empty = WorkoutLogEditorViewModel.toDraft(null, "UPPER_1", day, listOf(bench.copy(sets = listOf(LogSetUi()))))
        assertEquals(DraftResult.Invalid(uiText(R.string.workout_log_no_sets)), empty)
    }

    @Test
    fun `a stored set reads back into its row text`() {
        val row = WorkoutLogEditorViewModel.rowOf(bench.exercise, LoggedSet(0, 8, 82.5))
        assertEquals(LogSetUi("82.5", "8"), row)
        assertEquals(LogSetUi("", "30"), WorkoutLogEditorViewModel.rowOf(bike.exercise, LoggedSet(0, 0, 0.0, 30)))
    }
}
