package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros

enum class FoodSource { MINE, BUILT_IN, ONLINE }

/** A food that can be added to a meal: per 100 g macros plus the units it can be counted in (grams first). */
data class FoodChoice(
    val key: String,
    val name: String,
    /** Brand for online products, else null. Shown muted next to the name. */
    val detail: String?,
    val per100g: Macros,
    val units: List<FoodUnit>,
    val source: FoodSource,
)

/** `meal_log_items.foodKey` values. Stored, never shown. */
object FoodKey {
    private const val BUILT_IN = "b:" // not UI text: storage key prefix
    private const val MINE = "u:" // not UI text: storage key prefix
    private const val MEAL = "m:" // not UI text: storage key prefix

    // not UI text: storage key
    fun builtIn(usdaId: Long) = "$BUILT_IN$usdaId"
    // not UI text: storage key
    fun mine(userFoodId: Long) = "$MINE$userFoodId"
    // not UI text: storage key
    fun meal(mealId: Long) = "$MEAL$mealId"
    fun builtInId(key: String): Long? = key.takeIf { it.startsWith(BUILT_IN) }?.removePrefix(BUILT_IN)?.toLongOrNull()
    fun mineId(key: String): Long? = key.takeIf { it.startsWith(MINE) }?.removePrefix(MINE)?.toLongOrNull()
    fun mealId(key: String): Long? = key.takeIf { it.startsWith(MEAL) }?.removePrefix(MEAL)?.toLongOrNull()
}

fun BasicFood.toChoice() = FoodChoice(FoodKey.builtIn(usdaId), name, detail = null, per100g, units, FoodSource.BUILT_IN)
