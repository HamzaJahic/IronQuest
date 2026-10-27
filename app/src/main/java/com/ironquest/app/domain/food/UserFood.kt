package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros

enum class UserFoodSource { CUSTOM, ONLINE }

data class UserFood(
    val id: Long,
    val name: String,
    val per100g: Macros,
    val pieceGrams: Double?,
    val servingGrams: Double?,
    val source: UserFoodSource,
    val barcode: String?,
    val brand: String?,
) {
    val units: List<FoodUnit>
        get() = buildList {
            add(FoodUnit.GRAM)
            pieceGrams?.takeIf { it > 0 }?.let { add(FoodUnit(UnitKind.PIECE, it)) }
            servingGrams?.takeIf { it > 0 }?.let { add(FoodUnit(UnitKind.SERVING, it)) }
        }

    fun toChoice() = FoodChoice(FoodKey.mine(id), name, brand, per100g, units, FoodSource.MINE)
}

/** `id == null` creates. Macros are per 100 g; the form converts "per piece" before building this. */
data class UserFoodDraft(val id: Long?, val name: String, val per100g: Macros, val pieceGrams: Double?)
