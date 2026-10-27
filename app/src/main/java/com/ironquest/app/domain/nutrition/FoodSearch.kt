package com.ironquest.app.domain.nutrition

enum class FoodSearchError {
    /** No connection, DNS failure, or timeout. */
    OFFLINE,

    /** HTTP 429 from Open Food Facts (about 10 searches a minute). */
    RATE_LIMITED,

    /** Any other non-2xx status. */
    SERVER,

    /** 2xx but the body wasn't the JSON we expect. */
    BAD_RESPONSE,
}

/** Deliberately has no cause and no URL in its message: connection exceptions can contain the request URL, which holds the query. */
class FoodSearchException(val error: FoodSearchError) : Exception("Online food search failed: $error") // not UI text
