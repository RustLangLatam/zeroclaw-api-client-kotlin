package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A memory entry. */
@Serializable
data class MemoryEntry(
    /** The entry id. */
    val id: String,
    /** The key. */
    val key: String,
    /** The content. */
    val content: String,
    /** The category. */
    val category: String,
    /** When it was stored. */
    val timestamp: String,
    /** The session it came from. */
    val sessionId: String? = null,
    /** The relevance of a recall match. */
    val score: Double? = null,
    /** The namespace. */
    val namespace: String? = null,
    /** The importance. */
    val importance: Long? = null,
    /** The entry that replaced it. */
    val supersededBy: String? = null,
    /** `"episodic"`, `"procedural"` or `{"semantic": "fact" | …}`. */
    val kind: JsonElement? = null,
    /** Whether it is pinned. */
    val pinned: Boolean? = null,
    /** The owning agent. */
    val agentAlias: String? = null,
)

/** The filters of [MemoryApi.search]. */
@Serializable
data class MemorySearch(
    /** Text to match. */
    val query: String? = null,
    /** `core`, `daily`, `conversation` or custom. */
    val category: String? = null,
    /** Entries stored at or after (RFC 3339). */
    val since: String? = null,
    /** Entries stored before (RFC 3339). */
    val until: String? = null,
    /** Search this agent's memory instead of the shared one. */
    val agent: String? = null,
)

/** The long-term memory endpoints (`/api/memory`), from [Client.memory]. */
class MemoryApi internal constructor(
    private val c: Client,
) {
    /**
     * `GET /api/memory`: searches the memory. With a text (`query`) or time
     * (`since`, `until`) filter, the best matches (at most 50); otherwise every
     * entry (of `category`, when set). Contents longer than 4096 characters
     * come truncated.
     */
    suspend fun search(filter: MemorySearch = MemorySearch()): List<MemoryEntry> =
        c.call("GET", listOf("api", "memory"), "entries", query = query(filter))

    /**
     * `POST /api/memory`: stores `content` under `key`, in category `core`
     * unless `category` is set, and in `agent`'s memory or, without one, the
     * shared one.
     */
    suspend fun store(
        key: String,
        content: String,
        category: String? = null,
        agent: String? = null,
    ) = c.empty(
        "POST",
        listOf("api", "memory"),
        json =
            buildJsonObject {
                put("key", key)
                put("content", content)
                put("category", category?.ifEmpty { null })
                put("agent", agent?.ifEmpty { null })
            },
    )

    /**
     * `DELETE /api/memory/{key}`: forgets an entry, from `agent`'s memory or
     * the shared one, and reports whether it existed.
     */
    suspend fun forget(
        key: String,
        agent: String? = null,
    ): Boolean = c.call("DELETE", listOf("api", "memory", key), "deleted", query = mapOf("agent" to agent))
}
