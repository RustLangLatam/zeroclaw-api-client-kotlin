package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventsTest {
    private fun Gateway.stream(text: String) = server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(text))

    @Test
    fun `frames are data lines ended by a blank line`() =
        Gateway().use { gw ->
            gw.stream(": keep-alive\n\ndata: {\"type\":\"agent_end\",\"n\":1}\n\ndata: {\"@timestamp\":\"t\"}\n\n")
            val events =
                runBlocking {
                    gw.client.events
                        .subscribe()
                        .events()
                        .toList()
                }
            assertEquals(listOf("agent_end", ""), events.map { it.kind })
            assertTrue(events[1].isLog)
            assertEquals("text/event-stream", gw.server.takeRequest().getHeader("Accept"))
        }

    @Test
    fun `CRLF, multi-line data and a last frame without its blank line`() =
        Gateway().use { gw ->
            gw.stream("data: {\"type\":\r\ndata: \"a\"}\r\n\r\ndata:{\"type\":\"b\"}")
            val events =
                runBlocking {
                    gw.client.events
                        .subscribe()
                        .events()
                        .toList()
                }
            assertEquals(listOf("a", "b"), events.map { it.kind })
        }

    @Test
    fun `a frame that is not JSON throws, and the stream goes on`() =
        Gateway().use { gw ->
            gw.stream("data: nope\n\ndata: [1]\n\ndata: {\"type\":\"x\"}\n\n")
            runBlocking {
                gw.client.events.subscribe().use { stream ->
                    assertFailsWith<DecodeException> { stream.next() }
                    assertFailsWith<DecodeException> { stream.next() }
                    assertEquals("x", stream.next()!!.kind)
                    assertNull(stream.next())
                }
            }
        }

    @Test
    fun `a dropped stream is a TransportException`() =
        Gateway().use { gw ->
            gw.server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setChunkedBody("data: {\"type\":\"x\"}\n\ndata: {\"ty", 5)
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            runBlocking {
                gw.client.events.subscribe().use { stream ->
                    val first = runCatching { stream.next() }
                    if (first.isSuccess) assertFailsWith<TransportException> { while (stream.next() != null) Unit }
                }
            }
        }

    @Test
    fun `history returns the events`() =
        Gateway().use { gw ->
            gw.json("""{"events":[{"type":"tool_call"}]}""")
            assertEquals("tool_call", runBlocking { gw.client.events.history() }.single().kind)
        }
}
