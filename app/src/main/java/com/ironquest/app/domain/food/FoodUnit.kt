package com.ironquest.app.domain.food

enum class UnitKind { GRAM, MILLILITRE, PIECE, SERVING, PORTION, MEAL_PORTION }

/**
 * How an amount is counted. [gramsPerUnit] turns it into grams (1 for g; density for ml; piece or
 * portion weight). [label] is the USDA portion word ("cup", "tbsp"), English as USDA writes it;
 * only [UnitKind.PORTION] has one. [UnitKind.MEAL_PORTION] is a library meal's portion (1× = the
 * meal), its macros come from the meal, not from grams.
 */
data class FoodUnit(val kind: UnitKind, val gramsPerUnit: Double, val label: String? = null) {
    /** Stored in `meal_log_items.unitKey`. Never shown. */
    val storageKey: String
        get() = when (kind) {
            UnitKind.GRAM -> KEY_GRAM
            UnitKind.MILLILITRE -> KEY_ML
            UnitKind.PIECE -> KEY_PIECE
            UnitKind.SERVING -> KEY_SERVING
            UnitKind.PORTION -> KEY_PORTION_PREFIX + label.orEmpty()
            UnitKind.MEAL_PORTION -> KEY_MEAL_PORTION
        }

    companion object {
        private const val KEY_GRAM = "g" // not UI text: storage key
        private const val KEY_ML = "ml" // not UI text: storage key
        private const val KEY_PIECE = "pcs" // not UI text: storage key
        private const val KEY_SERVING = "serving" // not UI text: storage key
        private const val KEY_PORTION_PREFIX = "p:" // not UI text: storage key
        private const val KEY_MEAL_PORTION = "x" // not UI text: storage key

        val GRAM = FoodUnit(UnitKind.GRAM, 1.0)
        val MILLILITRE = FoodUnit(UnitKind.MILLILITRE, 1.0)
        val MEAL_PORTION = FoodUnit(UnitKind.MEAL_PORTION, 0.0)

        /** Unknown keys fall back to grams (stored data must never crash a read). */
        fun fromStorage(key: String, gramsPerUnit: Double?): FoodUnit = when {
            key == KEY_GRAM -> GRAM
            key == KEY_ML -> FoodUnit(UnitKind.MILLILITRE, gramsPerUnit ?: 1.0)
            key == KEY_PIECE -> FoodUnit(UnitKind.PIECE, gramsPerUnit ?: 0.0)
            key == KEY_SERVING -> FoodUnit(UnitKind.SERVING, gramsPerUnit ?: 0.0)
            key == KEY_MEAL_PORTION -> MEAL_PORTION
            key.startsWith(KEY_PORTION_PREFIX) -> FoodUnit(UnitKind.PORTION, gramsPerUnit ?: 0.0, key.removePrefix(KEY_PORTION_PREFIX))
            else -> GRAM
        }
    }
}
