package com.ironquest.app.domain.food

import com.ironquest.app.domain.nutrition.MealLogEntry

object Recents {
    const val LIMIT = 10

    /** [newestFirst] quick meals, without repeats of the same foods + amounts (order inside a meal ignored). */
    fun distinctMeals(newestFirst: List<MealLogEntry>, limit: Int): List<MealLogEntry> = newestFirst
        .filter { it.isQuickMeal }
        // not UI text: comparison key
        .distinctBy { log -> log.items.map { "${it.foodKey}|${it.amount}|${it.unit.storageKey}" }.sorted() }
        .take(limit)
}
