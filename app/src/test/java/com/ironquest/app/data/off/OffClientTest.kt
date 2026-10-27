package com.ironquest.app.data.off

import com.ironquest.app.data.net.HttpResponse
import com.ironquest.app.data.net.HttpTransport
import com.ironquest.app.domain.nutrition.FoodSearchError
import com.ironquest.app.domain.nutrition.FoodSearchException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OffClientTest {

    private val fixture =
        requireNotNull(javaClass.classLoader?.getResource("off/search_ok.json")).readText()

    private class FakeTransport(var respond: (String) -> HttpResponse) : HttpTransport {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            urls += url
            this.headers += headers
            return respond(url)
        }
    }

    private fun client(transport: FakeTransport) = OffClient(userAgent = AGENT, transport = transport, io = Dispatchers.Unconfined)

    private fun errorOf(result: Result<*>): FoodSearchError = (result.exceptionOrNull() as FoodSearchException).error

    @Test
    fun `search url encodes the query`() {
        assertTrue(OffClient.searchUrl("mac & cheese").contains("search_terms=mac+%26+cheese&"))
        assertTrue(OffClient.searchUrl("skyr").startsWith("https://world.openfoodfacts.org/cgi/search.pl?"))
    }

    @Test
    fun `a 200 response is parsed and the next identical query is cached`() = runTest {
        val transport = FakeTransport { HttpResponse(200, fixture) }
        val client = client(transport)
        assertEquals(2, client.search("skyr").getOrThrow().size)
        assertEquals(2, client.search("skyr").getOrThrow().size)
        assertEquals(1, transport.urls.size)
    }

    @Test
    fun `queries share the cache after trimming, collapsing spaces and lowercasing`() = runTest {
        val transport = FakeTransport { HttpResponse(200, fixture) }
        val client = client(transport)
        client.search("  Skyr  ")
        client.search("skyr")
        assertEquals(1, transport.urls.size)
    }

    @Test
    fun `http status codes map to typed errors`() = runTest {
        assertNull(OffClient.errorFor(200))
        assertEquals(FoodSearchError.RATE_LIMITED, errorOf(client(FakeTransport { HttpResponse(429, "") }).search("skyr")))
        assertEquals(FoodSearchError.SERVER, errorOf(client(FakeTransport { HttpResponse(500, "") }).search("skyr")))
    }

    @Test
    fun `network failures are OFFLINE without a cause`() = runTest {
        val result = client(FakeTransport { throw IOException("no route") }).search("skyr")
        assertEquals(FoodSearchError.OFFLINE, errorOf(result))
        assertNull(checkNotNull(result.exceptionOrNull()).cause)
    }

    @Test
    fun `a 200 body that is not JSON is BAD_RESPONSE`() = runTest {
        val result = client(FakeTransport { HttpResponse(200, "<html>oops</html>") }).search("skyr")
        assertEquals(FoodSearchError.BAD_RESPONSE, errorOf(result))
    }

    @Test
    fun `a blank query returns nothing without a request`() = runTest {
        val transport = FakeTransport { error("must not be called") }
        assertEquals(0, client(transport).search("   ").getOrThrow().size)
        assertEquals(0, transport.urls.size)
    }

    @Test
    fun `the request carries the User-Agent header`() = runTest {
        val transport = FakeTransport { HttpResponse(200, fixture) }
        client(transport).search("skyr")
        assertEquals(AGENT, transport.headers.single()["User-Agent"])
    }

    companion object {
        const val AGENT = "IronQuest/test (Android)"
    }
}
