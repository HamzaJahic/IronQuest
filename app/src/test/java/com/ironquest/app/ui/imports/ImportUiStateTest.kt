package com.ironquest.app.ui.imports

import com.ironquest.app.R
import com.ironquest.app.domain.importing.ImportKind
import com.ironquest.app.domain.model.DayImport
import com.ironquest.app.domain.model.ExerciseDefinition
import com.ironquest.app.domain.model.ExerciseImport
import com.ironquest.app.domain.model.RepRange
import com.ironquest.app.domain.model.StatType
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.text.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportUiStateTest {
    private val fly = ExerciseDefinition("x_fly_1", "Fly", StatType.STRENGTH)
    private val days = ParsedImport.Days(listOf(DayImport("Push", listOf(ExerciseImport(fly, 3, RepRange(8, 12))))))

    private fun lines(state: ImportUiState) = state.previewLines { "%02d:%02d".format(it / 60, it % 60) }.map(EnglishText::resolve)

    @Test
    fun `workout previews read as before`() {
        assertEquals(listOf("Push: Fly 3×8–12"), lines(ImportUiState(ImportKind.PROGRAM, parsed = days)))
        assertEquals(listOf("Fly 3×8–12"), lines(ImportUiState(ImportKind.EXERCISES, parsed = days)))
    }

    @Test
    fun `blockers say what is missing`() {
        val blocked = ImportUiState(ImportKind.EXERCISE, parsed = days)
        assertEquals(uiText(R.string.import_blocker_built_in_only), blocked.blocker)
        assertEquals(uiText(R.string.import_blocker_name), ImportUiState(ImportKind.PROGRAM, parsed = days).blocker)
        assertNull(ImportUiState(ImportKind.PROGRAM, parsed = days, name = "Mine").blocker)
    }
}
