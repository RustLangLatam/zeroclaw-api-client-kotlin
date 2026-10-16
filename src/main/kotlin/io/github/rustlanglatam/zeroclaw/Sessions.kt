package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A stored session. */
@Serializable
data class Session(
    /** The display id (`gw_` removed). */
    val sessionId: String,
    /** The full key (`gw_…`, `whatsapp_<channel>_<peer>…`). */
    val sessionKey: String? = null,
    /** The creation time (RFC 3339). */
    val createdAt: String,
    /** The last activity (RFC 3339). */
    val lastActivity: String,
    /** The number of messages in it. */
    val messageCount: Long,
    /** The agent that answers it. */
    val agentAlias: String? = null,
    /** The channel it came through (`whatsapp.alias`). */
    val channelId: String? = null,
    /** The name given to it. */
    val name: String? = null,
)

/** A message of a transcript. */
@Serializable
data class SessionMessage(
    /** `user`, `assistant`, `tool`, … */
    val role: String,
    /** The content (tool calls and results are JSON text). */
    val content: String,
    /** When it was stored. */
    val createdAt: String? = null,
)

/** Whether a session is busy. */
@Serializable
data class SessionState(
    /** The session id as asked. */
    val sessionId: String,
    /** `idle`, `running` or `error`. */
    val state: String,
    /** The running turn. */
    val turnId: String? = null,
    /** When it started. */
    val turnStartedAt: String? = null,
)

/** The conversation sessions endpoints (`/api/sessions`), from [Client.sessions]. */
class SessionsApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/sessions`: every stored session, empty when the gateway keeps none. */
    suspend fun list(): List<Session> = c.call("GET", listOf("api", "sessions"), "sessions")

    /** `GET /api/sessions/running`: the gateway (web chat) sessions. */
    suspend fun running(): List<Session> = c.call("GET", listOf("api", "sessions", "running"), "sessions")

    /** `GET /api/sessions/{id}/messages`: the transcript, empty for an unknown session. */
    suspend fun messages(id: String): List<SessionMessage> = c.call("GET", listOf("api", "sessions", id, "messages"), "messages")

    /**
     * `POST /api/sessions/{id}/messages`: appends an assistant message to the
     * session, shown to chat clients. No agent turn runs.
     */
    suspend fun addMessage(
        id: String,
        content: String,
    ) = c.empty("POST", listOf("api", "sessions", id, "messages"), json = buildJsonObject { put("content", content) })

    /** `GET /api/sessions/{id}/state`: whether a turn is running. */
    suspend fun state(id: String): SessionState = c.call("GET", listOf("api", "sessions", id, "state"))

    /** `PUT /api/sessions/{id}`: renames a session. */
    suspend fun rename(
        id: String,
        name: String,
    ) = c.empty("PUT", listOf("api", "sessions", id), json = buildJsonObject { put("name", name) })

    /** `DELETE /api/sessions/{id}`: cancels its running turn and deletes it. */
    suspend fun delete(id: String) = c.empty("DELETE", listOf("api", "sessions", id))

    /** `POST /api/sessions/{id}/abort`: cancels the running turn and reports whether there was one. */
    suspend fun abort(id: String): Boolean = c.call<String>("POST", listOf("api", "sessions", id, "abort"), "status") == "aborted"
}
