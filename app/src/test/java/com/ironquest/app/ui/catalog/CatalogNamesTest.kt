package com.ironquest.app.ui.catalog

import com.ironquest.app.domain.program.ProgramCatalog
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CatalogNamesTest {

    @Test
    fun `every catalog exercise has a name resource with its stored English name`() {
        ProgramCatalog.Exercises.all.forEach { exercise ->
            val id = CatalogNames.exercise(exercise.id)
            assertNotNull("No name resource for exercise ${exercise.id}", id)
            assertEquals(exercise.name, EnglishText.string(checkNotNull(id)))
            assertEquals(UiText.Res(id), exerciseNameText(exercise))
        }
    }

    @Test
    fun `every catalog template has a name resource with its stored English name`() {
        ProgramCatalog.templates.forEach { template ->
            val id = CatalogNames.template(template.id)
            assertNotNull("No name resource for template ${template.id}", id)
            assertEquals(template.name, EnglishText.string(checkNotNull(id)))
        }
    }

    @Test
    fun `imported names show as stored`() {
        assertEquals(UiText.Verbatim("Cable fly"), exerciseNameText("x_cable_fly_1a2b", "Cable fly"))
        assertEquals(UiText.Verbatim("Push"), templateNameText("C7", "Push"))
    }
}
