package com.ironquest.app.domain.nutrition

/**
 * Port to the Android reminder scheduler. Implementations must return immediately
 * (the real one conflates requests onto a background coroutine) and must never throw:
 * it is called after a write has already committed, so a failure here must not turn a
 * successful write into an error. (RearmingNutritionRepository also guards with runCatching.)
 */
fun interface ReminderRearmer {
    fun requestRearm()
}
