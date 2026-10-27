package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros

/** One Open Food Facts product. [per100g] has `kcal == null` when the product reports no energy. */
data class OnlineFood(val barcode: String, val name: String, val brand: String?, val per100g: Macros, val servingGrams: Double?) {
    fun toChoice() = FoodChoice(
        // not UI text: transient key, replaced by a u: key once picked
        key = "online:$barcode",
        name = name,
        detail = brand,
        per100g = per100g,
        units = buildList {
            add(FoodUnit.GRAM)
            servingGrams?.takeIf { it > 0 }?.let { add(FoodUnit(UnitKind.SERVING, it)) }
        },
        source = FoodSource.ONLINE,
    )
}
