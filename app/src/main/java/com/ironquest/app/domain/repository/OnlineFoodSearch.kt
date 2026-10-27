package com.ironquest.app.domain.repository

import com.ironquest.app.domain.food.OnlineFood

/** Open Food Facts search: the only network use in the app. */
interface OnlineFoodSearch {
    /** Failures are `Result.failure(FoodSearchException)`. Never throws, except `CancellationException`. */
    suspend fun search(query: String): Result<List<OnlineFood>>
}
