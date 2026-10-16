package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.time.Duration

const val TOKEN = "zc_test_token"

/** A mock gateway and a client of it, with retries that do not wait. */
class Gateway(
    timeout: Duration? = DEFAULT_TIMEOUT,
    retry: RetryPolicy? = RetryPolicy(baseDelay = Duration.ZERO, jitter = false),
) : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val client = Client(server.url("/").toString(), ClientOptions(token = TOKEN, timeout = timeout, retry = retry))

    fun json(
        body: String,
        status: Int = 200,
    ) = server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))

    override fun close() = server.shutdown()
}

/** `text` as JSON. */
fun obj(text: String): JsonElement = ZeroClawJson.parseToJsonElement(text)

/** What a call returned, as JSON with the wire's names (`null` for nothing). */
fun toJson(value: Any?): JsonElement =
    when (value) {
        null, Unit -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is List<*> -> JsonArray(value.map(::toJson))
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        else -> ZeroClawJson.encodeToJsonElement(serializer(value::class.java), value)
    }

/** Whether every value of `expected` is in `actual`, the same ([JsonNull] matches a missing field). */
fun contains(
    actual: JsonElement?,
    expected: JsonElement,
): Boolean =
    when (expected) {
        is JsonObject ->
            actual is JsonObject &&
                expected.all { (k, v) -> if (v == JsonNull) actual[k] == null || actual[k] == JsonNull else contains(actual[k], v) }
        is JsonArray -> actual is JsonArray && actual.size == expected.size && expected.indices.all { contains(actual[it], expected[it]) }
        is JsonPrimitive ->
            actual is JsonPrimitive &&
                (
                    actual == expected ||
                        (
                            actual.isString == expected.isString &&
                                actual.content.toDoubleOrNull() != null &&
                                actual.content.toDouble() == expected.content.toDoubleOrNull()
                        )
                )
    }
