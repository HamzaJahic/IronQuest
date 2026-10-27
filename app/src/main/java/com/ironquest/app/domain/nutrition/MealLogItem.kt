package com.ironquest.app.domain.nutrition

import com.ironquest.app.domain.food.FoodUnit

/** One food inside a quick meal. [macros] is the snapshot for [amount], kcal resolved. */
data class MealLogItem(
    val foodKey: String,
    val name: String,
    val amount: Double,
    val unit: FoodUnit,
    val macros: Macros,
) {
    /** Same food, new amount (same unit): macros scale linearly, so editing needs no food lookup. */
    fun withAmount(newAmount: Double): MealLogItem = copy(amount = newAmount, macros = if (amount > 0) macros * (newAmount / amount) else macros)
}
