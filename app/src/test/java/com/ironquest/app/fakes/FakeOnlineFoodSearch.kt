package com.ironquest.app.fakes

import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.repository.OnlineFoodSearch
import kotlinx.coroutines.delay

/** Records every query; [delayMs] simulates a slow network on virtual time. */
class FakeOnlineFoodSearch : OnlineFoodSearch {
    val queries = mutableListOf<String>()
    var delayMs = 0L

    /** Per-query delay; defaults to [delayMs]. */
    var delayFor: (String) -> Long = { delayMs }
    var respond: (String) -> Result<List<OnlineFood>> = { Result.success(emptyList()) }

    override suspend fun search(query: String): Result<List<OnlineFood>> {
        queries += query
        val waitMs = delayFor(query)
        if (waitMs > 0) delay(waitMs)
        return respond(query)
    }
}
