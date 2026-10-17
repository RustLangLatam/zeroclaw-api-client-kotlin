package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.Closeable

/** The WebSocket subprotocol ZeroClaw's chat speaks. */
const val SUBPROTOCOL = "zeroclaw.v1"

/**
 * Chat frame types. Frames of any other type (`plan`, `history_trimmed`,
 * `cron_result`, session events, …) keep their type and only [ChatFrame.raw]
 * is set.
 */
object FrameType {
    /** The session is open. */
    const val SESSION_START = "session_start"

    /** Answer to a `connect` frame. */
    const val CONNECTED = "connected"

    /** A piece of the reply, in `content`. */
    const val CHUNK = "chunk"

    /** A piece of the model's reasoning, in `content`. */
    const val THINKING = "thinking"

    /** The agent calls a tool. */
    const val TOOL_CALL = "tool_call"

    /** A tool's result. */
    const val TOOL_RESULT = "tool_result"

    /** The agent needs approval to run a tool. */
    const val APPROVAL_REQUEST = "approval_request"

    /** The turn ended; see [ChatFrame.done]. */
    const val DONE = "done"

    /** The turn was cancelled. */
    const val ABORTED = "aborted"

    /** The turn (or the session) failed. */
    const val ERROR = "error"
}

/** The end of a turn. */
@Serializable
data class TurnResult(
    /** The whole reply. */
    val fullResponse: String = "",
    /** Input tokens used. */
    val inputTokens: Long? = null,
    /** Output tokens used. */
    val outputTokens: Long? = null,
    /** Tokens used in all. */
    val tokensUsed: Long? = null,
    /** Cost of the turn, in USD. */
    val costUsd: Double? = null,
    /** The model used. */
    val model: String? = null,
    /** The provider used. */
    val provider: String? = null,
    /** The model's context size. */
    val maxContextTokens: Long? = null,
    /** The input tokens of the last model call. */
    val lastInputTokens: Long? = null,
)

/** A frame the gateway sends on the chat. [type] says which fields are set. */
data class ChatFrame(
    /** The frame type, one of [FrameType] or another. */
    val type: String,
    /** Content of a chunk or thinking frame. */
    val content: String = "",
    /** Session id of a session_start frame. */
    val sessionId: String = "",
    /** Whether the session existed (session_start). */
    val resumed: Boolean = false,
    /** Messages the session holds (session_start). */
    val messageCount: Int = 0,
    /** Name of the session (session_start) or of the tool (tool_call, tool_result). */
    val name: String = "",
    /** Id of a tool_call or tool_result. */
    val id: String = "",
    /** Arguments of a tool_call. */
    val args: JsonElement? = null,
    /** Output of a tool_result. */
    val output: JsonElement? = null,
    /** Request id of an approval_request; answer it with [ChatSession.answerApproval]. */
    val requestId: String = "",
    /** Tool of an approval_request. */
    val tool: String = "",
    /** What the tool would do (approval_request). */
    val argumentsSummary: String = "",
    /** Seconds before the approval_request is denied. */
    val timeoutSecs: Int = 0,
    /** The end of the turn, for a done frame. */
    val done: TurnResult? = null,
    /** Message of an error frame. */
    val message: String = "",
    /** Code of an error frame (`PROVIDER_ERROR`, `SESSION_QUEUE_FULL`, …). */
    val code: String = "",
    /** The frame as sent. */
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        /** Parses a chat frame; [DecodeException] when it is not a JSON object. */
        @JvmStatic
        fun parse(data: String): ChatFrame {
            val raw =
                try {
                    ZeroClawJson.parseToJsonElement(data)
                } catch (e: SerializationException) {
                    throw DecodeException("chat frame is not JSON: ${e.message}", e)
                } as? JsonObject ?: throw DecodeException("chat frame is not a JSON object")

            fun str(key: String) = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

            fun int(key: String) = (raw[key] as? JsonPrimitive)?.intOrNull ?: 0
            val frame = ChatFrame(type = str("type"), raw = raw)
            return when (frame.type) {
                FrameType.SESSION_START ->
                    frame.copy(
                        sessionId = str("session_id"),
                        resumed = (raw["resumed"] as? JsonPrimitive)?.booleanOrNull == true,
                        messageCount = int("message_count"),
                        name = str("name"),
                    )
                FrameType.CHUNK, FrameType.THINKING -> frame.copy(content = str("content"))
                FrameType.TOOL_CALL -> frame.copy(id = str("id"), name = str("name"), args = raw["args"])
                FrameType.TOOL_RESULT -> frame.copy(id = str("id"), name = str("name"), output = raw["output"])
                FrameType.APPROVAL_REQUEST ->
                    frame.copy(
                        requestId = str("request_id"),
                        tool = str("tool"),
                        argumentsSummary = str("arguments_summary"),
                        timeoutSecs = int("timeout_secs"),
                    )
                FrameType.DONE ->
                    frame.copy(
                        done =
                            runCatching { decode(raw, TurnResult.serializer()) }
                                .getOrDefault(TurnResult())
                                .copy(fullResponse = str("full_response")),
                    )
                FrameType.ERROR ->
                    frame.copy(
                        message = str("message").ifEmpty { str("error") }.ifEmpty { "unknown error" },
                        code = str("code"),
                    )
                else -> frame
            }
        }
    }
}

/** An open WebSocket read one text message at a time. */
open class Socket internal constructor(
    private val ws: WebSocket,
    private val incoming: Channel<String>,
) : Closeable {
    /** The next text message, or `null` once the socket closed. */
    protected suspend fun read(): String? {
        val result = incoming.receiveCatching()
        result.exceptionOrNull()?.let { throw it }
        return result.getOrNull()
    }

    protected fun write(frame: JsonObject) {
        if (!ws.send(frame.toString())) throw WebSocketException("websocket error: the connection is closed")
    }

    /** Closes the connection. */
    override fun close() {
        ws.close(1000, null)
        incoming.close()
    }
}

/**
 * An open chat with an agent, from [Client.chat].
 *
 * Frames are read by one consumer at a time, with [next] or [frames] (which
 * ends once the socket closed). Closing during a turn cancels it.
 */
class ChatSession private constructor(
    ws: WebSocket,
    incoming: Channel<String>,
) : Socket(ws, incoming) {
    /** The session id (its key is `gw_<id>`). */
    var sessionId: String = ""
        private set

    /** Whether an existing session was resumed. */
    var resumed: Boolean = false
        private set

    /** The number of messages the session held when it was opened. */
    var messageCount: Int = 0
        private set

    /** Sends a message: it starts a turn, or steers the running one. */
    fun send(content: String) =
        write(
            buildJsonObject {
                put("type", "message")
                put("content", content)
            },
        )

    /** Answers an approval_request with `approve`, `deny` or `always`. */
    fun answerApproval(
        requestId: String,
        decision: String,
    ) = write(
        buildJsonObject {
            put("type", "approval_response")
            put("request_id", requestId)
            put("decision", decision)
        },
    )

    /** The next frame from the gateway, or `null` once the socket closed. */
    suspend fun next(): ChatFrame? = read()?.let(ChatFrame::parse)

    /** The frames, until the socket closed. */
    fun frames(): Flow<ChatFrame> =
        flow {
            while (true) emit(next() ?: break)
        }

    /**
     * Sends `content` and waits for the turn to end, ignoring the frames in
     * between. An error frame throws a [TurnException]; an aborted turn too,
     * with code `ABORTED`. Tool approvals are not answered here: read the
     * frames for agents that ask for them.
     */
    suspend fun ask(content: String): TurnResult {
        send(content)
        while (true) {
            val frame = next() ?: throw WebSocketException("the gateway closed the chat before the turn ended")
            when (frame.type) {
                FrameType.DONE -> return frame.done ?: TurnResult()
                FrameType.ERROR -> throw TurnException(frame.code, frame.message)
                FrameType.ABORTED -> throw TurnException("ABORTED", "the turn was aborted")
            }
        }
    }

    internal companion object {
        suspend fun open(
            client: Client,
            alias: String,
            sessionId: String?,
            name: String?,
            cwd: String?,
        ): ChatSession {
            val (ws, incoming) =
                openSocket(
                    client,
                    listOf("ws", "chat"),
                    mapOf("agent" to alias, "session_id" to sessionId, "name" to name, "cwd" to cwd),
                )
            val session = ChatSession(ws, incoming)
            try {
                val frame = session.next() ?: throw WebSocketException("the gateway closed the chat before the session started")
                if (frame.type == FrameType.ERROR) throw TurnException(frame.code, frame.message)
                if (frame.type != FrameType.SESSION_START) throw WebSocketException("expected session_start, got ${frame.raw}")
                session.sessionId = frame.sessionId
                session.resumed = frame.resumed
                session.messageCount = frame.messageCount
                return session
            } catch (e: Throwable) {
                session.close()
                throw e
            }
        }
    }
}

/**
 * A live WebSocket feed of JSON frames the gateway pushes (canvas, SOP runs).
 *
 * Read it with [next] or [frames]; it ends once the gateway closed the feed. A
 * frame that is not JSON throws a [DecodeException] from [next]; the feed goes
 * on after it.
 */
class Feed internal constructor(
    socket: Pair<WebSocket, Channel<String>>,
) : Socket(socket.first, socket.second) {
    /** The next frame, or `null` once the gateway closed the feed. */
    suspend fun next(): JsonElement? {
        val data = read() ?: return null
        return try {
            ZeroClawJson.parseToJsonElement(data)
        } catch (e: SerializationException) {
            throw DecodeException("frame is not JSON: ${e.message}", e)
        }
    }

    /** The frames, until the gateway closed the feed. */
    fun frames(): Flow<JsonElement> =
        flow {
            while (true) emit(next() ?: break)
        }
}

/**
 * Opens a WebSocket to `segments` on the gateway, offering ZeroClaw's
 * subprotocol and the bearer token (in `Sec-WebSocket-Protocol` as
 * `bearer.<token>`, and as an `Authorization` header). The client's timeout
 * bounds the handshake. A refused handshake (401 without a valid token, 400
 * for an unknown agent) is a [WebSocketException] whose `status` (and
 * [statusCode]) is the HTTP status.
 */
internal suspend fun openSocket(
    client: Client,
    segments: List<String>,
    query: Map<String, Any?> = emptyMap(),
): Pair<WebSocket, Channel<String>> {
    val token = client.token()
    val protocols = if (token == null) SUBPROTOCOL else "$SUBPROTOCOL, bearer.$token"
    val request =
        Request
            .Builder()
            .url(client.url(segments, query))
            .header("Sec-WebSocket-Protocol", protocols)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
    val incoming = Channel<String>(Channel.UNLIMITED)
    val opened = CompletableDeferred<Unit>()
    val ws =
        client.http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    opened.complete(Unit)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    incoming.trySend(text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    webSocket.close(1000, null)
                    incoming.close()
                    opened.completeExceptionally(WebSocketException("websocket error: the gateway refused the connection ($code)"))
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    val status = response?.code
                    response?.close()
                    if (opened.isActive) {
                        val reason =
                            if (status !=
                                null
                            ) {
                                "the gateway refused the connection ($status)"
                            } else {
                                (t.message ?: "connection failed")
                            }
                        opened.completeExceptionally(WebSocketException("websocket error: $reason", t, status ?: 0))
                    }
                    incoming.close(WebSocketException("websocket error: connection lost", t))
                }
            },
        )
    try {
        val timeout = client.timeout
        if (timeout == null) opened.await() else withTimeout(timeout) { opened.await() }
    } catch (_: TimeoutCancellationException) {
        ws.cancel()
        throw RequestTimeoutException()
    } catch (e: Throwable) {
        ws.cancel()
        throw e
    }
    return ws to incoming
}
