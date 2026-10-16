package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChatTest {
    /** Serves one WebSocket: `onOpen` gets the gateway's side, `onText` each frame the client sends. */
    private fun Gateway.socket(
        onOpen: (WebSocket) -> Unit,
        onText: (WebSocket, String) -> Unit = { _, _ -> },
    ) = server.enqueue(
        MockResponse().withWebSocketUpgrade(
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) = onOpen(webSocket)

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) = onText(webSocket, text)

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    webSocket.close(1000, null)
                }
            },
        ),
    )

    private val start = """{"type":"session_start","session_id":"s1","resumed":true,"message_count":4,"name":"n"}"""

    @Test
    fun `a chat opens on ws chat with the subprotocol and the token`() =
        Gateway().use { gw ->
            gw.socket({ it.send(start) })
            runBlocking {
                gw.client.chat("helper", sessionId = "s1").use { chat ->
                    assertEquals("s1", chat.sessionId)
                    assertTrue(chat.resumed)
                    assertEquals(4, chat.messageCount)
                }
            }
            val seen = gw.server.takeRequest()
            assertEquals("/ws/chat", seen.requestUrl!!.encodedPath)
            assertEquals("helper", seen.requestUrl!!.queryParameter("agent"))
            assertEquals("s1", seen.requestUrl!!.queryParameter("session_id"))
            assertEquals("zeroclaw.v1, bearer.$TOKEN", seen.getHeader("Sec-WebSocket-Protocol"))
        }

    @Test
    fun `ask sends the message and returns the end of the turn`() =
        Gateway().use { gw ->
            gw.socket({ it.send(start) }) { ws, text ->
                assertEquals(obj("""{"type":"message","content":"hi"}"""), obj(text))
                ws.send("""{"type":"chunk","content":"he"}""")
                ws.send("""{"type":"done","full_response":"hello","input_tokens":3,"cost_usd":0.01}""")
            }
            val result = runBlocking { gw.client.chat("helper").use { it.ask("hi") } }
            assertEquals("hello", result.fullResponse)
            assertEquals(3, result.inputTokens)
            assertEquals(0.01, result.costUsd)
        }

    @Test
    fun `an error frame fails the turn with its code, an abort with ABORTED`() =
        Gateway().use { gw ->
            var turn = 0
            gw.socket({ it.send(start) }) { ws, _ ->
                ws.send(if (turn++ == 0) """{"type":"error","message":"boom","code":"PROVIDER_ERROR"}""" else """{"type":"aborted"}""")
            }
            runBlocking {
                gw.client.chat("helper").use { chat ->
                    assertEquals("PROVIDER_ERROR", assertFailsWith<TurnException> { chat.ask("a") }.code)
                    assertEquals("ABORTED", assertFailsWith<TurnException> { chat.ask("b") }.code)
                }
            }
        }

    @Test
    fun `frames are read until the gateway closes the chat`() =
        Gateway().use { gw ->
            gw.socket(onOpen = {
                it.send(start)
                it.send("""{"type":"tool_call","id":"t1","name":"shell","args":{"cmd":"ls"}}""")
                it.send("""{"type":"plan","steps":[]}""")
                it.close(1000, null)
            })
            val frames = runBlocking { gw.client.chat("helper").use { it.frames().toList() } }
            assertEquals(listOf("tool_call", "plan"), frames.map { it.type })
            assertEquals(obj("""{"cmd":"ls"}"""), frames[0].args)
        }

    @Test
    fun `an error before the session starts fails the chat`() =
        Gateway().use { gw ->
            gw.socket({ it.send("""{"type":"error","message":"unknown agent","code":"AGENT_NOT_FOUND"}""") })
            assertEquals("AGENT_NOT_FOUND", assertFailsWith<TurnException> { runBlocking { gw.client.chat("nobody") } }.code)
        }

    @Test
    fun `a refused handshake is a WebSocketException naming the status`() =
        Gateway().use { gw ->
            gw.json("""{"error":"unauthorized"}""", status = 401)
            val e = assertFailsWith<WebSocketException> { runBlocking { gw.client.chat("helper") } }
            assertTrue(e.message!!.contains("401"), e.message)
        }

    @Test
    fun `a feed yields JSON frames`() =
        Gateway().use { gw ->
            gw.socket(onOpen = {
                it.send("""{"type":"connected"}""")
                it.send("""{"type":"frame","frame":{"content":"x"}}""")
                it.close(1000, null)
            })
            val frames =
                runBlocking {
                    gw.client.canvas
                        .watch("c1")
                        .use { it.frames().toList() }
                }
            assertEquals(2, frames.size)
            assertEquals(
                "/ws/canvas/c1",
                gw.server
                    .takeRequest()
                    .requestUrl!!
                    .encodedPath,
            )
        }

    @Test
    fun `frames parse their fields by type`() {
        val approval =
            ChatFrame.parse(
                """{"type":"approval_request","request_id":"r","tool":"shell","arguments_summary":"ls","timeout_secs":30}""",
            )
        assertEquals(listOf("r", "shell", "ls"), listOf(approval.requestId, approval.tool, approval.argumentsSummary))
        assertEquals(30, approval.timeoutSecs)
        assertEquals("unknown error", ChatFrame.parse("""{"type":"error"}""").message)
        assertFailsWith<DecodeException> { ChatFrame.parse("[1]") }
    }
}
