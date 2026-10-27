package com.ironquest.app.notifications

import com.ironquest.app.R
import com.ironquest.app.domain.nutrition.Macros
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import kotlin.math.roundToInt

/** Notification copy. Pure so it can be unit-tested; resolved when the notification is posted. */
object ReminderCopy {
    val markEaten: UiText = uiText(R.string.notification_action_mark_eaten)
    val taken: UiText = uiText(R.string.notification_action_taken)
    val supplementBody: UiText = uiText(R.string.notification_supplement_body)

    fun slotTitle(label: String, offsetMin: Int): UiText = uiText(R.string.notification_slot_title, label, offsetMin)

    fun slotBody(mealName: String, macros: Macros): UiText = uiText(
        R.string.notification_slot_body,
        mealName,
        macros.proteinG.roundToInt(),
        macros.carbsG.roundToInt(),
        macros.fatG.roundToInt(),
    )

    fun supplementTitle(name: String, dose: String): UiText = if (dose.isBlank()) {
        uiText(R.string.notification_supplement_title, name)
    } else {
        uiText(R.string.notification_supplement_title_dose, name, dose.trim())
    }
}
