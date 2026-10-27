package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.Macros

/** One food from the bundled USDA list. [units] always starts with grams. */
data class BasicFood(
    val usdaId: Long,
    val name: String,
    val aliases: List<String>,
    val per100g: Macros,
    val units: List<FoodUnit>,
)

data class BasicFoodTable(val foods: List<BasicFood>, val skipped: Int)
