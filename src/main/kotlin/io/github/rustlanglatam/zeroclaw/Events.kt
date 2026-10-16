package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Response
import java.io.Closeable
import java.io.IOException

/** One event, as the gateway sent it: its top-level fields. */
typealias GatewayEvent = JsonObject

/** The event's `type` (`agent_end`, `cron_result`, …); empty for log events. */
val GatewayEvent.kind: String get() = (this["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** Whether it is a structured log event (`@timestamp`, `event`, `service`, …). */
val GatewayEvent.isLog: Boolean get() = "@timestamp" in this

/**
 * The gateway's live event stream (Server-Sent Events) and its recent
 * history (`/api/events`), from [Client.events].
 *
 * Gateway handler: `crates/zeroclaw-gateway/src/sse.rs`. Every frame is
 * `data: <one JSON object>` with no event name, so frames are told apart by
 * their content: observer events and cron results carry a `type`
 * (`llm_request`, `tool_call`, `tool_call_start`, `agent_start`, `agent_end`,
 * `error`, `history_trimmed`, `cron_result`, …); structured log events carry
 * `@timestamp` instead. Frames missed while lagging are not replayed.
 */
class EventsApi internal constructor(
    private val c: Client,
) {
    /**
     * `GET /api/events`: opens the live stream. It ends when the gateway
     * closes the connection (e.g. on reload); subscribe again to resume. The
     * client's timeout bounds only the wait for the stream to open, never the
     * stream itself. Close the stream when done.
     */
    suspend fun subscribe(): EventStream =
        EventStream(
            c.send(
                "GET",
                listOf("api", "events"),
                headers =
                    mapOf(
                        "Accept" to "text/event-stream",
                    ),
            ),
        )

    /**
     * `GET /api/events/history`: the latest observer events, oldest first. Up
     * to 500, kept in memory by the gateway.
     */
    suspend fun history(): List<GatewayEvent> = c.call("GET", listOf("api", "events", "history"), "events")
}

/**
 * The live stream of [EventsApi.subscribe].
 *
 * Read it with [next] or [events]; it ends once the gateway closed the
 * stream. A frame that is not JSON throws a [DecodeException] from [next];
 * the stream goes on after it. Any other error ends the stream.
 */
class EventStream internal constructor(
    private val response: Response,
) : Closeable {
    private val source = response.body?.source()

    // ponytail: a blocking read on the IO dispatcher; cancelling the coroutine
    // does not interrupt it, close() from elsewhere does.

    /** The next event, or `null` once the gateway closed the stream. */
    suspend fun next(): GatewayEvent? =
        withContext(Dispatchers.IO) {
            val data = mutableListOf<String>()
            while (true) {
                // "data:" lines are joined until a blank line ends the frame;
                // comments (keep-alives) and other fields are skipped. A last
                // frame without its blank line still counts.
                val line =
                    try {
                        source?.readUtf8Line()
                    } catch (e: IOException) {
                        throw TransportException("request to the gateway failed: reading the event stream: ${e.message}", e)
                    }
                if (line != null && line.startsWith("data:")) {
                    data += line.removePrefix("data:").removePrefix(" ")
                    continue
                }
                if (line == null && data.isEmpty()) return@withContext null
                if (!line.isNullOrEmpty() || data.isEmpty()) continue
                val event: JsonElement =
                    try {
                        ZeroClawJson.parseToJsonElement(data.joinToString("\n"))
                    } catch (e: SerializationException) {
                        throw DecodeException("event is not JSON: ${e.message}", e)
                    }
                return@withContext event as? JsonObject ?: throw DecodeException("event is not a JSON object")
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

    /** The events, until the gateway closed the stream; the stream is closed after. */
    fun events(): Flow<GatewayEvent> =
        flow {
            use { while (true) emit(next() ?: break) }
        }

    /** Closes the stream. */
    override fun close() = response.close()
}
