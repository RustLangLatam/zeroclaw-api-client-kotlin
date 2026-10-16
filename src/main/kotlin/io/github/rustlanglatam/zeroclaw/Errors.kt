package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Statuses that ask the client to try again later. */
internal val RETRYABLE_STATUSES = setOf(408, 429, 502, 503, 504)

/**
 * Base class of every error the client throws.
 *
 * A gateway answer with a non-success status is an [ApiException] carrying the
 * status, the gateway's message and its JSON body.
 */
open class ZeroClawException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** The gateway URL, or a URL built from it, is not valid. */
class InvalidUrlException(
    message: String,
) : ZeroClawException(message)

/**
 * The request never got an HTTP answer: connection refused, DNS or TLS
 * failure, or a connection dropped before the answer. The underlying error is
 * the `cause`.
 */
class TransportException(
    message: String,
    cause: Throwable? = null,
) : ZeroClawException(message, cause)

/** The gateway did not answer within the client's timeout (after any retries). */
class RequestTimeoutException(
    message: String = "the gateway did not answer in time",
) : ZeroClawException(message)

/** The gateway answered successfully but the body did not have the expected shape. */
class DecodeException(
    message: String,
    cause: Throwable? = null,
) : ZeroClawException(message, cause)

/** A WebSocket connection to the gateway failed. */
class WebSocketException(
    message: String,
    cause: Throwable? = null,
) : ZeroClawException(message, cause)

/**
 * A gateway answer with a non-success status. Its message reads
 * `gateway answered <status>: <reason>`.
 */
class ApiException(
    /** The HTTP status code. */
    val status: Int,
    /**
     * The gateway's own explanation (its `error` or `message` field) when the
     * body carries one, otherwise the body text.
     */
    val reason: String,
    /** The JSON error body, when the gateway sent one. */
    val body: JsonElement? = null,
) : ZeroClawException("gateway answered $status: $reason") {
    companion object {
        /** Builds the error from a failed answer's status and body text. */
        @JvmStatic
        fun fromResponse(
            status: Int,
            text: String,
        ): ApiException {
            val body =
                try {
                    ZeroClawJson.parseToJsonElement(text)
                } catch (_: Exception) {
                    return ApiException(status, text.trim())
                }
            if (body is JsonObject) {
                for (key in listOf("error", "message")) {
                    val value = body[key] ?: continue
                    val reason = if (value is JsonPrimitive && value.isString) value.content else value.toString()
                    return ApiException(status, reason, body)
                }
            }
            return ApiException(status, text.trim(), body)
        }
    }
}

/** An agent turn on the chat that ended in an `error` frame, or was aborted. */
class TurnException(
    /** The stable code, e.g. `PROVIDER_ERROR` or `ABORTED`; empty when the gateway sent none. */
    val code: String,
    message: String,
) : ZeroClawException("agent turn failed: $message")

/** The HTTP status of an [ApiException], or 0 for any other error. */
val Throwable.statusCode: Int get() = (this as? ApiException)?.status ?: 0

/** Whether the gateway answered 404 (the resource does not exist). */
val Throwable.isNotFound: Boolean get() = statusCode == 404

/** Whether the gateway refused the credentials (401) or the action (403). */
val Throwable.isUnauthorized: Boolean get() = statusCode == 401 || statusCode == 403

/** Whether the gateway rate-limited the call (429). */
val Throwable.isRateLimited: Boolean get() = statusCode == 429

/**
 * Whether the call may work if made again later: no connection, a connection
 * dropped before the answer, a timeout, or a "try again" status (408, 429,
 * 502, 503, 504). The client already retried idempotent calls before throwing
 * it.
 */
val Throwable.isTransient: Boolean
    get() =
        when (this) {
            is ApiException -> status in RETRYABLE_STATUSES
            is TransportException, is RequestTimeoutException -> true
            else -> false
        }
