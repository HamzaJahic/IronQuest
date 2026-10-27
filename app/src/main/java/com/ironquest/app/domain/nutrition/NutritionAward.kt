package com.ironquest.app.domain.nutrition

import com.ironquest.app.domain.model.LevelUpEvent

/** Net character XP change of one write (can be negative) and any level-up it caused. */
data class NutritionAward(
    val xpDelta: Int,
    val levelUp: LevelUpEvent?,
    /** True when the write was a no-op because it had already happened (double tap, notification + app). */
    val alreadyDone: Boolean = false,
) {
    companion object {
        val NONE = NutritionAward(xpDelta = 0, levelUp = null)
    }
}

/** What [com.ironquest.app.domain.repository.NutritionRepository.savePlan] did: the plan id and the XP change from re-evaluating today's bonuses. */
data class SavePlanResult(val planId: Long, val award: NutritionAward)
