package com.ironquest.app.domain.nutrition

/** Which daily targets apply today. Past days keep their `nutrition_days` snapshot instead. */
object TargetResolution {
    fun resolve(mode: FoodTrackingMode, planTargets: MacroTargets?, ownTargets: MacroTargets): MacroTargets? {
        val own = ownTargets.takeIf { it != MacroTargets() }
        return when (mode) {
            FoodTrackingMode.ON_THE_FLY -> own
            FoodTrackingMode.PLAN, FoodTrackingMode.BOTH -> planTargets ?: own
        }
    }
}
