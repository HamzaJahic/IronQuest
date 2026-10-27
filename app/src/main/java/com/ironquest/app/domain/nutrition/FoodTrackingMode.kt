package com.ironquest.app.domain.nutrition

/** Chosen at first launch, changed on the You tab. Order matters for the setup screen. */
enum class FoodTrackingMode {
    PLAN,
    ON_THE_FLY,
    BOTH,
    ;

    /** Plan checklist, Plans link, meal reminders. */
    val showsPlan: Boolean get() = this != ON_THE_FLY

    /** "+ Log food" opens the meal builder (Plan mode keeps the library picker). */
    val showsBuilder: Boolean get() = this != PLAN

    companion object {
        val DEFAULT = BOTH
    }
}

data class FoodSettings(val mode: FoodTrackingMode = FoodTrackingMode.DEFAULT, val ownTargets: MacroTargets = MacroTargets())
