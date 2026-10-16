package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * How long a request waits for the gateway's answer (its headers: a streamed
 * body is not bounded). The gateway's own limit is 30 s; calls that do longer
 * work set their own.
 */
val DEFAULT_TIMEOUT: Duration = 60.seconds

/**
 * The JSON settings of the client: snake_case on the wire, unknown fields
 * ignored and absent optional fields left `null`, so a nearby gateway build
 * still decodes. Use it to decode the [JsonElement]s some calls return.
 */
val ZeroClawJson: Json =
    Json {
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        explicitNulls = false
        coerceInputValues = true
    }

/**
 * When and how often a failed idempotent request (GET, HEAD, OPTIONS, PUT,
 * DELETE) is tried again: after a connection failure, a timeout or a "try
 * again" status (408, 429, 502, 503, 504). A POST or PATCH is never retried:
 * it could apply twice.
 *
 * The wait before retry `n` (0-based) is `baseDelay * 2^n`, capped at
 * [maxDelay]; with [jitter], a random point in its upper half. A
 * `Retry-After` on a 429 or 503 is honored, up to [maxDelay].
 */
data class RetryPolicy(
    /** How many times a request is tried again at most. */
    val maxRetries: Int = 3,
    /** The wait before the first retry. */
    val baseDelay: Duration = 200.milliseconds,
    /** The cap on the wait between retries. */
    val maxDelay: Duration = 5.seconds,
    /** Randomize each wait between half and all of its delay. */
    val jitter: Boolean = true,
)

/** The options of a [Client]. */
data class ClientOptions(
    /**
     * The bearer token sent with every request (obtained by pairing, see
     * [Client.pairing]). Without one, only public endpoints answer.
     */
    val token: String? = null,
    /**
     * How long a request waits for the gateway's answer. It bounds the headers
     * only, so the event stream is not cut. `null` waits without limit.
     */
    val timeout: Duration? = DEFAULT_TIMEOUT,
    /** How failed idempotent requests are retried; `null` turns retries off. */
    val retry: RetryPolicy? = RetryPolicy(),
    /**
     * The OkHttp client to send requests with: add interceptors for logging,
     * tracing or request signing. Give it no read timeout (the event stream
     * and long runs stay quiet for minutes) and no redirects. A shared default
     * is used without one.
     */
    val httpClient: OkHttpClient? = null,
) {
    override fun toString(): String = "ClientOptions(token=${if (token == null) "null" else "***"}, timeout=$timeout, retry=$retry)"
}

/**
 * A connection to one ZeroClaw gateway.
 *
 * ```kotlin
 * val client = Client("http://127.0.0.1:42617", ClientOptions(token = token))
 * val health = client.system.health()
 * ```
 *
 * Resource groups are reached through properties such as [cron] or [config].
 * A client holds no connection of its own, so it needs no closing; open chats,
 * feeds and event streams do. From Java, build one with [Client.builder].
 *
 * @throws InvalidUrlException `baseUrl` is not an absolute http or https URL.
 */
class Client
    @JvmOverloads
    constructor(
        baseUrl: String,
        private val options: ClientOptions = ClientOptions(),
    ) {
        /** The gateway's base URL, without a trailing slash. */
        val baseUrl: String

        private val base: HttpUrl

        /** How long a request waits for the gateway's answer (`null`: no limit). */
        val timeout: Duration? = options.timeout

        internal val http: OkHttpClient = options.httpClient ?: defaultHttp

        private val retry: RetryPolicy = options.retry ?: RetryPolicy(maxRetries = 0)

        init {
            val url = baseUrl.toHttpUrlOrNull()
            if (url == null || url.host.isEmpty() || !baseUrl.contains("://")) {
                throw InvalidUrlException("\"$baseUrl\" is not an absolute http or https URL")
            }
            // Segments are appended to the base path, so it must not end in "/".
            val segments = url.pathSegments.filter { it.isNotEmpty() }
            base =
                url
                    .newBuilder()
                    .encodedPath("/")
                    .apply { segments.forEach { addPathSegment(it) } }
                    .query(null)
                    .fragment(null)
                    .build()
            this.baseUrl = base.toString().trimEnd('/')
        }

        /** A client for the same gateway and options with another bearer token, e.g. one a pairing returned. */
        fun withToken(token: String?): Client = Client(baseUrl, options.copy(token = token))

        /** Whether the client sends a token. The token itself is never exposed. */
        val authenticated: Boolean get() = !options.token.isNullOrEmpty()

        /** Describes the client without its token, so it never reaches logs. */
        override fun toString(): String = "Client($baseUrl, authenticated=$authenticated)"

        // Resource groups.

        /** One agent's endpoints: its workspace, personality files and skills. */
        fun agent(alias: String): AgentApi = AgentApi(this, alias)

        /** The canvas endpoints (`/api/canvas`). */
        val canvas: CanvasApi get() = CanvasApi(this)

        /** The channel endpoints (`/api/channels`). */
        val channels: ChannelsApi get() = ChannelsApi(this)

        /** The configuration endpoints (`/api/config`). */
        val config: ConfigApi get() = ConfigApi(this)

        /** The usage and cost endpoint (`/api/cost`). */
        val cost: CostApi get() = CostApi(this)

        /** The scheduled jobs endpoints (`/api/cron`). */
        val cron: CronApi get() = CronApi(this)

        /** The live event stream endpoints (`/api/events`). */
        val events: EventsApi get() = EventsApi(this)

        /** The shared directory and image uploads (`/api/browse`, `/api/upload`). */
        val files: FilesApi get() = FilesApi(this)

        /** The long-term memory endpoints (`/api/memory`). */
        val memory: MemoryApi get() = MemoryApi(this)

        /** The pairing and paired devices endpoints (`/api/pair`, `/api/devices`, …). */
        val pairing: PairingApi get() = PairingApi(this)

        /** The first-agent quickstart endpoints (`/api/quickstart`). */
        val quickstart: QuickstartApi get() = QuickstartApi(this)

        /** The conversation sessions endpoints (`/api/sessions`). */
        val sessions: SessionsApi get() = SessionsApi(this)

        /** The skill bundles and skills (`/api/skills`). */
        val skills: SkillsApi get() = SkillsApi(this)

        /** The standard operating procedure endpoints (`/api/sops`). */
        val sops: SopsApi get() = SopsApi(this)

        /** Health, status, diagnostics, logs, versions and administration. */
        val system: SystemApi get() = SystemApi(this)

        /** The hardware-key registration and assertion endpoints (`/api/webauthn`). */
        val webauthn: WebAuthnApi get() = WebAuthnApi(this)

        /** The generic webhook and A2A endpoints (`/webhook`, `/a2a`). */
        val webhook: WebhookApi get() = WebhookApi(this)

        /**
         * `GET /ws/chat`: opens a chat with agent `alias` over a WebSocket and
         * waits for the gateway to confirm the session. `sessionId` resumes or
         * creates that session (a new one without it), `name` names it and `cwd`
         * is its working directory (it must exist on the host). Close the
         * session when done.
         */
        suspend fun chat(
            alias: String,
            sessionId: String? = null,
            name: String? = null,
            cwd: String? = null,
        ): ChatSession = ChatSession.open(this, alias, sessionId, name, cwd)

        // Requests.

        /** The URL of `segments` under the base, each percent-encoded. */
        internal fun url(
            segments: List<String>,
            query: Map<String, Any?> = emptyMap(),
        ): HttpUrl =
            base
                .newBuilder()
                .apply {
                    segments.forEach { addPathSegment(it) }
                    for ((key, value) in query) {
                        if (value == null || value == "") continue
                        addQueryParameter(key, value.toString())
                    }
                }.build()

        /** The bearer token, for the WebSocket handshake. */
        internal fun token(): String? = options.token?.takeIf { it.isNotEmpty() }

        /**
         * Sends a request; a non-success status throws an [ApiException]. The
         * caller closes the answer.
         */
        internal suspend fun send(
            method: String,
            segments: List<String>,
            query: Map<String, Any?> = emptyMap(),
            json: JsonElement? = null,
            body: RequestBody? = null,
            headers: Map<String, String> = emptyMap(),
            timeout: Duration? = this.timeout,
        ): Response {
            val requestBody =
                when {
                    json != null -> json.toString().toRequestBody(JSON_TYPE)
                    body != null -> body
                    method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody()
                    else -> null
                }
            val request =
                Request
                    .Builder()
                    .url(url(segments, query))
                    .method(method, requestBody)
                    .apply {
                        headers.forEach { (k, v) -> header(k, v) }
                        token()?.let { header("Authorization", "Bearer $it") }
                    }.build()
            val retries = if (method in IDEMPOTENT) retry.maxRetries else 0
            var attempt = 0
            while (true) {
                val response =
                    try {
                        attempt(request, timeout)
                    } catch (e: ZeroClawException) {
                        if (attempt < retries && e.isTransient) {
                            delay(wait(attempt, null))
                            attempt++
                            continue
                        }
                        throw e
                    }
                if (response.isSuccessful) return response
                if (attempt < retries && response.code in RETRYABLE_STATUSES) {
                    val pause = wait(attempt, response)
                    response.close()
                    delay(pause)
                    attempt++
                    continue
                }
                val text = response.use { runCatching { it.body?.string() }.getOrNull().orEmpty() }
                throw ApiException.fromResponse(response.code, text)
            }
        }

        /** One try; the timeout bounds the wait for the answer's headers. */
        private suspend fun attempt(
            request: Request,
            timeout: Duration?,
        ): Response {
            val call = http.newCall(request)
            return try {
                if (timeout == null) call.await() else withTimeout(timeout) { call.await() }
            } catch (_: TimeoutCancellationException) {
                throw RequestTimeoutException()
            } catch (e: IOException) {
                throw TransportException("request to the gateway failed: ${e.message}", e)
            }
        }

        /** The wait before retry `attempt` (0-based) after `response`. */
        private fun wait(
            attempt: Int,
            response: Response?,
        ): Duration {
            val base = retry.baseDelay.inWholeMilliseconds
            val max = retry.maxDelay.inWholeMilliseconds
            var delay = if (base <= 0) 0L else min(base * (1L shl min(attempt, 20)), max)
            if (retry.jitter && delay > 0) delay = delay / 2 + Random.nextLong(delay / 2 + 1)
            val after = retryAfter(response)
            return (if (after > delay) min(after, max) else delay).milliseconds
        }

        /** Makes a call and returns its JSON answer ([JsonNull] for an empty body), or one field of it. */
        @PublishedApi
        internal suspend fun element(
            method: String,
            segments: List<String>,
            field: String? = null,
            query: Map<String, Any?> = emptyMap(),
            json: JsonElement? = null,
            body: RequestBody? = null,
            headers: Map<String, String> = emptyMap(),
            timeout: Duration? = this.timeout,
        ): JsonElement {
            val text = text(method, segments, query, json, body, headers, timeout)
            val element =
                if (text.isBlank()) {
                    JsonNull
                } else {
                    try {
                        ZeroClawJson.parseToJsonElement(text)
                    } catch (e: SerializationException) {
                        throw DecodeException("unexpected gateway response: ${e.message}", e)
                    }
                }
            if (field == null) return element
            return (element as? JsonObject)?.get(field)
                ?: throw DecodeException("unexpected gateway response: missing field \"$field\"")
        }

        /** Makes a call and returns its body as text. */
        internal suspend fun text(
            method: String,
            segments: List<String>,
            query: Map<String, Any?> = emptyMap(),
            json: JsonElement? = null,
            body: RequestBody? = null,
            headers: Map<String, String> = emptyMap(),
            timeout: Duration? = this.timeout,
        ): String =
            send(method, segments, query, json, body, headers, timeout).use {
                try {
                    it.body?.string().orEmpty()
                } catch (e: IOException) {
                    throw TransportException("request to the gateway failed: reading the answer: ${e.message}", e)
                }
            }

        /** Makes a call, ignoring its body. */
        internal suspend fun empty(
            method: String,
            segments: List<String>,
            query: Map<String, Any?> = emptyMap(),
            json: JsonElement? = null,
            headers: Map<String, String> = emptyMap(),
        ) {
            text(method, segments, query, json, null, headers)
        }

        /** Opens a server-push WebSocket feed on `segments`. */
        internal suspend fun feed(segments: List<String>): Feed = Feed(openSocket(this, segments))

        /** Builds a [Client] from Java. */
        class Builder internal constructor(
            private val baseUrl: String,
        ) {
            private var options = ClientOptions()

            /** The bearer token. */
            fun token(token: String?) = apply { options = options.copy(token = token) }

            /** How long a request waits for the answer, in milliseconds; 0 waits without limit. */
            fun timeoutMillis(millis: Long) = apply { options = options.copy(timeout = if (millis <= 0) null else millis.milliseconds) }

            /** How many times idempotent requests are retried; 0 turns retries off. */
            fun maxRetries(retries: Int) = apply { options = options.copy(retry = RetryPolicy(maxRetries = retries)) }

            /** The OkHttp client to send requests with. */
            fun httpClient(http: OkHttpClient) = apply { options = options.copy(httpClient = http) }

            /** The client. */
            fun build() = Client(baseUrl, options)
        }

        companion object {
            /** A builder for a client of the gateway at `baseUrl`, for Java callers. */
            @JvmStatic
            fun builder(baseUrl: String): Builder = Builder(baseUrl)

            private val IDEMPOTENT = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")
            private val JSON_TYPE = "application/json".toMediaType()

            /** No read timeout (event streams, long runs) and no redirects. */
            private val defaultHttp: OkHttpClient by lazy {
                OkHttpClient
                    .Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .followRedirects(false)
                    .build()
            }
        }
    }

/** Calls and decodes the answer (or one field of it) as [T]. */
internal suspend inline fun <reified T> Client.call(
    method: String,
    segments: List<String>,
    field: String? = null,
    query: Map<String, Any?> = emptyMap(),
    json: JsonElement? = null,
    body: RequestBody? = null,
    headers: Map<String, String> = emptyMap(),
    timeout: Duration? = this.timeout,
): T = decode(element(method, segments, field, query, json, body, headers, timeout), serializer<T>())

/** Decodes `element` as [T], or throws a [DecodeException]. */
@PublishedApi
internal fun <T> decode(
    element: JsonElement,
    serializer: KSerializer<T>,
): T =
    try {
        ZeroClawJson.decodeFromJsonElement(serializer, element)
    } catch (e: SerializationException) {
        throw DecodeException("unexpected gateway response: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw DecodeException("unexpected gateway response: ${e.message}", e)
    }

/** `value` as a JSON request body, with the wire's snake_case names; `null` fields are left out. */
internal inline fun <reified T> body(value: T): JsonElement = ZeroClawJson.encodeToJsonElement(value)

private suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) = continuation.resume(response) { _, value, _ -> value.close() }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            },
        )
    }

/** The wait a 429 or 503 asks for in `Retry-After` (seconds or an HTTP date), in ms, or 0. */
private fun retryAfter(response: Response?): Long {
    if (response == null || (response.code != 429 && response.code != 503)) return 0
    val value = response.header("Retry-After")?.trim().orEmpty()
    value.toLongOrNull()?.let { return it * 1000 }
    val date = response.headers.getDate("Retry-After") ?: return 0
    return maxOf(0, date.time - System.currentTimeMillis())
}

/** A request object's set fields as query parameters, with the wire's names. */
internal inline fun <reified T> query(value: T): Map<String, Any?> =
    (body(value) as JsonObject).mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
