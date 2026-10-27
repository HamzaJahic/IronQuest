package com.ironquest.app.data.off

import com.ironquest.app.data.net.HttpTransport
import com.ironquest.app.data.net.HttpUrlConnectionTransport
import com.ironquest.app.domain.food.OnlineFood
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import com.ironquest.app.domain.repository.OnlineFoodSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import java.io.IOException
import java.net.URLEncoder

/**
 * Open Food Facts search. Request and parsing run on [io], never on main. Successful results are cached in
 * memory per normalised query. Failures carry only a [FoodSearchError], no cause and no URL.
 */
class OffClient(
    private val userAgent: String,
    private val transport: HttpTransport = HttpUrlConnectionTransport,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : OnlineFoodSearch {

    private val cache = object : LinkedHashMap<String, List<OnlineFood>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<OnlineFood>>?): Boolean = size > CACHE_SIZE
    }

    override suspend fun search(query: String): Result<List<OnlineFood>> {
        val normalised = query.trim().replace(WHITESPACE, " ").lowercase()
        if (normalised.isEmpty()) return Result.success(emptyList())
        synchronized(cache) { cache[normalised] }?.let { return Result.success(it) }
        return try {
            // A superseded search (user kept typing) is cancelled; the socket read itself may still run to its 8 s timeout.
            val foods = withTimeout(TOTAL_TIMEOUT_MS) { runInterruptible(io) { fetch(normalised) } }
            synchronized(cache) { cache[normalised] = foods }
            Result.success(foods)
        } catch (searchError: FoodSearchException) {
            Result.failure(searchError)
        } catch (timeout: TimeoutCancellationException) {
            // Our own withTimeout (a caller cancellation would be a plain CancellationException).
            Result.failure(FoodSearchException(FoodSearchError.OFFLINE))
        } catch (error: Exception) {
            // Never throws (see OnlineFoodSearch). No cause attached, by habit: transport exceptions can hold the URL.
            if (error is CancellationException) throw error
            Result.failure(FoodSearchException(FoodSearchError.OFFLINE))
        }
    }

    /** Blocking; always called on [io]. */
    private fun fetch(query: String): List<OnlineFood> {
        val headers = mapOf(
            "User-Agent" to userAgent, // not UI text: HTTP header
            "Accept" to "application/json", // not UI text: HTTP header
        )
        val response = try {
            transport.get(searchUrl(query), headers)
        } catch (ioError: IOException) {
            // Includes SocketTimeoutException and UnknownHostException.
            throw FoodSearchException(FoodSearchError.OFFLINE)
        }
        errorFor(response.status)?.let { throw FoodSearchException(it) }
        return try {
            OffParser.parseSearch(response.body)
        } catch (jsonError: JSONException) {
            throw FoodSearchException(FoodSearchError.BAD_RESPONSE)
        } catch (runtimeError: RuntimeException) {
            // A parser bug or odd payload after a 2xx is a bad response, not "offline".
            throw FoodSearchException(FoodSearchError.BAD_RESPONSE)
        }
    }

    companion object {
        const val CACHE_SIZE = 32
        const val TOTAL_TIMEOUT_MS = 20_000L
        private val WHITESPACE = Regex("\\s+")

        // not UI text: Open Food Facts request URL
        private const val BASE_URL = "https://world.openfoodfacts.org/cgi/search.pl"

        // not UI text: Open Food Facts request parameters
        private const val PARAMETERS = "&search_simple=1&action=process&json=1&page_size=20&fields=code,product_name,brands,nutriments,serving_quantity"

        fun searchUrl(query: String): String = "$BASE_URL?search_terms=${URLEncoder.encode(query, "UTF-8")}$PARAMETERS" // not UI text: Open Food Facts request URL

        fun errorFor(status: Int): FoodSearchError? = when (status) {
            in 200..299 -> null
            429 -> FoodSearchError.RATE_LIMITED
            else -> FoodSearchError.SERVER
        }
    }
}
