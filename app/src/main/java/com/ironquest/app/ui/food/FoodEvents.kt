package com.ironquest.app.ui.food

import com.ironquest.app.R
import com.ironquest.app.domain.model.LevelUpEvent
import com.ironquest.app.domain.nutrition.DeleteOutcome
import com.ironquest.app.domain.nutrition.NutritionAward
import com.ironquest.app.ui.kit.reachedLine
import com.ironquest.app.ui.text.UiText
import com.ironquest.app.ui.text.uiText
import kotlinx.coroutines.channels.SendChannel

/** One-shot events for the Food screens (never modelled as state). */
sealed interface FoodEvent {
    data class XpChanged(val amount: Int) : FoodEvent
    data class LevelUp(val event: LevelUpEvent) : FoodEvent
    data class Message(val text: UiText) : FoodEvent
}

internal suspend fun SendChannel<FoodEvent>.sendAward(award: NutritionAward) {
    if (award.xpDelta != 0) send(FoodEvent.XpChanged(award.xpDelta))
    award.levelUp?.let { send(FoodEvent.LevelUp(it)) }
}

/** "+25 XP" / "−10 XP". */
fun xpChangedText(amount: Int): UiText = if (amount > 0) uiText(R.string.common_xp_gain, amount) else uiText(R.string.common_xp_loss, -amount)

/** Snackbar text for an event. */
fun FoodEvent.text(): UiText = when (this) {
    is FoodEvent.XpChanged -> xpChangedText(amount)
    is FoodEvent.LevelUp -> event.reachedLine()
    is FoodEvent.Message -> text
}

/** Snackbar text shown on the screen the user lands on after deleting a meal. */
fun DeleteOutcome.message(): UiText = when (this) {
    DeleteOutcome.DELETED -> uiText(R.string.food_meal_deleted)
    DeleteOutcome.ARCHIVED -> uiText(R.string.food_meal_archived)
}
