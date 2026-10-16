package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ClientTest {
    @Test
    fun `the base URL must be absolute http or https`() {
        for (bad in listOf("127.0.0.1:42617", "ftp://gw", "/api", "")) {
            assertFailsWith<InvalidUrlException>(bad) { Client(bad) }
        }
        assertEquals("http://gw:1/zc", Client("http://gw:1/zc/").baseUrl)
    }

    @Test
    fun `path segments are escaped one by one under the base path`() {
        val c = Client("http://gw/base/")
        assertEquals("/base/api/memory/a%2Fb/x%20y/+34600@g.us", c.url(listOf("api", "memory", "a/b", "x y", "+34600@g.us")).encodedPath)
    }

    @Test
    fun `unset, null and empty query values are not sent`() {
        val url = Client("http://gw").url(listOf("x"), mapOf("a" to null, "b" to "", "c" to 1, "d" to true))
        assertEquals("c=1&d=true", url.encodedQuery)
    }

    @Test
    fun `the token is sent but never shown`() =
        Gateway().use { gw ->
            gw.json("""{"runs":[]}""")
            runBlocking { gw.client.cron.runs("j") }
            assertEquals("Bearer $TOKEN", gw.server.takeRequest().getHeader("Authorization"))
            assertFalse(gw.client.toString().contains(TOKEN))
            assertFalse(ClientOptions(token = TOKEN).toString().contains(TOKEN))
            assertTrue(gw.client.authenticated)
        }

    @Test
    fun `withToken keeps the gateway and swaps the token`() =
        Gateway().use { gw ->
            gw.json("{}")
            val other = gw.client.withToken("other")
            runCatching { runBlocking { other.cron.settings() } }
            assertEquals("Bearer other", gw.server.takeRequest().getHeader("Authorization"))
            assertFalse(gw.client.withToken(null).authenticated)
        }

    @Test
    fun `a refusal keeps the status, the gateway's reason and its body`() =
        Gateway().use { gw ->
            gw.json("""{"error":"unknown agent","code":"x"}""", status = 404)
            val e = assertFailsWith<ApiException> { runBlocking { gw.client.cron.list() } }
            assertEquals(404, e.status)
            assertEquals("unknown agent", e.reason)
            assertEquals("gateway answered 404: unknown agent", e.message)
            assertEquals(obj("""{"error":"unknown agent","code":"x"}"""), e.body)
            assertTrue(e.isNotFound)
        }

    @Test
    fun `fromResponse reads error, message or the text`() {
        assertEquals("a", ApiException.fromResponse(400, """{"error":"a"}""").reason)
        assertEquals("b", ApiException.fromResponse(400, """{"message":"b"}""").reason)
        assertEquals("""{"n":1}""", ApiException.fromResponse(400, """{"error":{"n":1}}""").reason)
        assertEquals("plain", ApiException.fromResponse(500, " plain ").reason)
    }

    @Test
    fun `errors are classified`() {
        assertTrue(ApiException(401, "").isUnauthorized && ApiException(403, "").isUnauthorized)
        assertTrue(ApiException(429, "").isRateLimited && ApiException(429, "").isTransient)
        assertTrue(ApiException(503, "").isTransient && !ApiException(500, "").isTransient)
        assertTrue(TransportException("x").isTransient && RequestTimeoutException().isTransient)
        assertEquals(0, DecodeException("x").statusCode)
    }

    @Test
    fun `idempotent calls are retried on a transient answer`() =
        Gateway().use { gw ->
            gw.json("{}", status = 503)
            gw.json("{}", status = 502)
            gw.json("""{"jobs":[]}""")
            assertEquals(emptyList(), runBlocking { gw.client.cron.list() })
            assertEquals(3, gw.server.requestCount)
        }

    @Test
    fun `retries give up after maxRetries`() =
        Gateway(retry = RetryPolicy(maxRetries = 2, baseDelay = 0.seconds)).use { gw ->
            repeat(3) { gw.json("{}", status = 503) }
            assertEquals(503, assertFailsWith<ApiException> { runBlocking { gw.client.cron.list() } }.status)
            assertEquals(3, gw.server.requestCount)
        }

    @Test
    fun `a POST is never retried, nor a 500`() =
        Gateway().use { gw ->
            gw.json("{}", status = 503)
            assertFailsWith<ApiException> { runBlocking { gw.client.system.doctor() } }
            gw.json("{}", status = 500)
            assertFailsWith<ApiException> { runBlocking { gw.client.cron.list() } }
            assertEquals(2, gw.server.requestCount)
        }

    @Test
    fun `connection failures are retried, then reported as TransportException`() =
        Gateway(retry = RetryPolicy(maxRetries = 1, baseDelay = 0.seconds)).use { gw ->
            repeat(2) { gw.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
            assertFailsWith<TransportException> { runBlocking { gw.client.cron.list() } }
            assertEquals(2, gw.server.requestCount)
        }

    @Test
    fun `no retry policy turns retries off`() =
        Gateway(retry = null).use { gw ->
            gw.json("{}", status = 503)
            assertFailsWith<ApiException> { runBlocking { gw.client.cron.list() } }
            assertEquals(1, gw.server.requestCount)
        }

    @Test
    fun `Retry-After is honored up to maxDelay`() =
        Gateway(retry = RetryPolicy(maxRetries = 1, baseDelay = 0.seconds, maxDelay = 300.milliseconds)).use { gw ->
            gw.server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            gw.json("""{"jobs":[]}""")
            val started = System.nanoTime()
            runBlocking { gw.client.cron.list() }
            val waited = (System.nanoTime() - started) / 1_000_000
            assertTrue(waited in 250..5000, "waited $waited ms")
        }

    @Test
    fun `the timeout bounds the wait for the answer`() =
        Gateway(timeout = 200.milliseconds, retry = null).use { gw ->
            gw.server.enqueue(MockResponse().setHeadersDelay(2, java.util.concurrent.TimeUnit.SECONDS).setBody("{}"))
            assertFailsWith<RequestTimeoutException> { runBlocking { gw.client.cron.settings() } }
        }

    @Test
    fun `a body that is not JSON is a DecodeException, an empty one is null`() =
        Gateway().use { gw ->
            gw.json("not json")
            assertFailsWith<DecodeException> { runBlocking { gw.client.quickstart.state() } }
            gw.json("")
            assertEquals(JsonNull, runBlocking { gw.client.quickstart.state() })
        }

    @Test
    fun `the Java builder sets the options`() {
        val c =
            Client
                .builder("http://gw")
                .token("t")
                .timeoutMillis(0)
                .maxRetries(0)
                .build()
        assertTrue(c.authenticated)
        assertEquals(null, c.timeout)
    }
}
