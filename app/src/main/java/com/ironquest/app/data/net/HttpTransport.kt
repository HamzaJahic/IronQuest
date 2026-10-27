package com.ironquest.app.data.net

import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResponse(val status: Int, val body: String)

/** Blocking GET. An interface so the clients' status and caching logic runs in JVM tests without a network. */
interface HttpTransport {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

object HttpUrlConnectionTransport : HttpTransport {
    const val TIMEOUT_MS = 8_000
    const val MAX_BODY_BYTES = 4 * 1024 * 1024

    /** Reads at most [MAX_BODY_BYTES]; a larger body is a [FoodSearchError.BAD_RESPONSE] (no cause, no URL). */
    private fun readLimited(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val bytesRead = input.read(buffer)
            if (bytesRead < 0) break
            if (out.size() + bytesRead > MAX_BODY_BYTES) throw FoodSearchException(FoodSearchError.BAD_RESPONSE)
            out.write(buffer, 0, bytesRead)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET" // not UI text: HTTP method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // not UI text: HTTP header name
            connection.setRequestProperty("Accept", "application/json")
            for ((name, value) in headers) connection.setRequestProperty(name, value)
            val status = connection.responseCode
            // errorStream for 4xx/5xx: getInputStream() would throw FileNotFoundException whose message is
            // the full URL, including the query.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readLimited(it) }.orEmpty()
            return HttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
