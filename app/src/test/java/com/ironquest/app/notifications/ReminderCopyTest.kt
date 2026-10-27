package com.ironquest.app.notifications

import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.fakes.EnglishText
import com.ironquest.app.ui.text.uiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderCopyTest {
    @Test
    fun `slot title names the label and offset`() {
        assertEquals(uiText(R.string.notification_slot_title, "Breakfast", 15), ReminderCopy.slotTitle("Breakfast", 15))
        assertEquals("Breakfast in 15 min", EnglishText.resolve(ReminderCopy.slotTitle("Breakfast", 15)))
    }

    @Test
    fun `slot body shows the meal and rounded 1x macros`() {
        val body = ReminderCopy.slotBody("Oats", Macros(35.4, 75.6, 9.0, 9.0))
        assertEquals(uiText(R.string.notification_slot_body, "Oats", 35, 76, 9), body)
        assertEquals("Oats · P35 C76 F9", EnglishText.resolve(body))
    }

    @Test
    fun `supplement title includes the dose only when there is one`() {
        assertEquals(uiText(R.string.notification_supplement_title_dose, "Creatine", "5 g"), ReminderCopy.supplementTitle("Creatine", "5 g"))
        assertEquals(uiText(R.string.notification_supplement_title, "Zinc"), ReminderCopy.supplementTitle("Zinc", " "))
        assertEquals("Time for Creatine (5 g)", EnglishText.resolve(ReminderCopy.supplementTitle("Creatine", "5 g")))
        assertEquals("Time for Zinc", EnglishText.resolve(ReminderCopy.supplementTitle("Zinc", " ")))
    }

    @Test
    fun `action labels and supplement body`() {
        assertEquals("Mark eaten", EnglishText.resolve(ReminderCopy.markEaten))
        assertEquals("Taken", EnglishText.resolve(ReminderCopy.taken))
        assertEquals("Tap Taken to check it off", EnglishText.resolve(ReminderCopy.supplementBody))
    }
}
