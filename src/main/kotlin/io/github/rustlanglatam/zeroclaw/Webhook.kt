package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** An agent's reply to a webhook. */
@Serializable
data class AgentReply(
    /** The reply. */
    val response: String,
    /** The model that answered. */
    val model: String,
)

/** The options of [WebhookApi.send]. */
data class WebhookOptions(
    /** The agent to answer (the default agent without one). */
    val agent: String? = null,
    /** Sent as `X-Webhook-Secret`, when the gateway sets `webhook_secret`. */
    val secret: String? = null,
    /**
     * Sent as `X-Idempotency-Key`: a repeat within the gateway's window is not
     * run again, and answers `duplicate`.
     */
    val idempotencyKey: String? = null,
    /**
     * Sent as `X-Session-Id`: it keeps calls in one conversation
     * (`[A-Za-z0-9._-]`, at most 128 characters).
     */
    val sessionId: String? = null,
)

/** The answer of [WebhookApi.send]: the agent's reply, or `duplicate` for a repeated idempotency key. */
@Serializable
data class WebhookReply(
    /** Whether the gateway saw the idempotency key already and ran nothing. */
    val duplicate: Boolean,
    /** The reply; `null` for a duplicate. */
    val reply: AgentReply? = null,
)

/** The generic webhook and A2A endpoints (`/webhook`, `/a2a`), from [Client.webhook]. */
class WebhookApi internal constructor(
    private val c: Client,
) {
    /** `POST /webhook`: sends `message` and returns the agent's reply. */
    suspend fun send(
        message: String,
        options: WebhookOptions = WebhookOptions(),
    ): WebhookReply {
        val headers =
            buildMap {
                options.secret?.takeIf { it.isNotEmpty() }?.let { put("X-Webhook-Secret", it) }
                options.idempotencyKey?.takeIf { it.isNotEmpty() }?.let { put("X-Idempotency-Key", it) }
                options.sessionId?.takeIf { it.isNotEmpty() }?.let { put("X-Session-Id", it) }
            }
        val raw =
            c.element(
                "POST",
                listOf("webhook"),
                query = mapOf("agent" to options.agent),
                json = buildJsonObject { put("message", message) },
                headers = headers,
            ) as? JsonObject
        if ((raw?.get("status") as? JsonPrimitive)?.content == "duplicate") return WebhookReply(duplicate = true)
        val response = raw?.get("response") as? JsonPrimitive
        if (response == null || !response.isString) throw DecodeException("unexpected gateway response: missing field \"response\"")
        return WebhookReply(duplicate = false, reply = decode(raw, AgentReply.serializer()))
    }

    /**
     * `POST /a2a/{alias}`: sends `text` to a published agent with
     * `message/send` and returns the text of its answer. A JSON-RPC error is
     * an [ApiException] with status 200. Needs the `a2a` feature and
     * `a2a.server.enabled` on the gateway.
     */
    suspend fun a2aSend(
        alias: String,
        text: String,
    ): String {
        val raw =
            c.element(
                "POST",
                listOf("a2a", alias),
                json =
                    buildJsonObject {
                        put("jsonrpc", "2.0")
                        put("id", 1)
                        put("method", "message/send")
                        putJsonObject("params") {
                            putJsonObject("message") {
                                putJsonArray("parts") {
                                    addJsonObject {
                                        put("kind", "text")
                                        put("text", text)
                                    }
                                }
                            }
                        }
                    },
            ) as? JsonObject ?: throw DecodeException("unexpected gateway response: not a JSON-RPC object")
        val error = raw["error"]
        if (error != null && error != JsonNull) {
            val message = ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.takeIf { it.isString }?.content
            throw ApiException(200, message ?: "A2A error", raw)
        }
        val artifacts = ((raw["result"] as? JsonObject)?.get("artifacts") as? kotlinx.serialization.json.JsonArray).orEmpty()
        return artifacts
            .flatMap { ((it as? JsonObject)?.get("parts") as? kotlinx.serialization.json.JsonArray).orEmpty() }
            .mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .joinToString("\n")
    }

    /** `GET /a2a/{alias}/.well-known/agent-card.json`: a published agent's A2A card. Public. */
    suspend fun agentCard(alias: String): JsonElement = c.element("GET", listOf("a2a", alias, ".well-known", "agent-card.json"))

    /**
     * `GET /.well-known/agents-card.json`: the gateway's A2A catalog card,
     * whose skills list the published agents. Public. 404 when the gateway was
     * built without the `a2a` feature or `[a2a.server] enabled` is false.
     */
    suspend fun agentCatalog(): JsonElement = c.element("GET", listOf(".well-known", "agents-card.json"))
}
