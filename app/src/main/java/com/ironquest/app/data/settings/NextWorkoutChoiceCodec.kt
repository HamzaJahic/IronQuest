package com.ironquest.app.data.settings

import com.ironquest.app.domain.program.NextWorkoutChoice

/** The "Do next" choice in `app_settings`. Reads raw values so a wrong type is no choice instead of a ClassCastException. */
object NextWorkoutChoiceCodec {
    const val KEY_TEMPLATE = "next_workout_template" // not UI text: SharedPreferences key
    const val KEY_CHOSEN_AT = "next_workout_chosen_at" // not UI text: SharedPreferences key

    fun decode(template: Any?, chosenAt: Any?): NextWorkoutChoice? {
        val templateId = (template as? String)?.takeIf { it.isNotBlank() } ?: return null
        val millis = chosenAt as? Long ?: return null
        return NextWorkoutChoice(templateId, millis)
    }
}
