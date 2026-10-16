package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Per-agent resources: the agent's workspace files, personality files and
// skills. Gateway handlers: `crates/zeroclaw-gateway/src/api_browse.rs`,
// `api_personality.rs` and `api_skills.rs`. Workspace paths are relative to
// the agent's workspace; escaping it is refused, and the personality files
// and sessions at its root are protected.

/** Fills the default personality files of [AgentApi.personalityTemplates]; unset fields are not sent. */
@Serializable
data class PersonalityContext(
    /** The agent's name. */
    val agentName: String? = null,
    /** The user's name. */
    val userName: String? = null,
    /** The time zone. */
    val timezone: String? = null,
    /** The communication style. */
    val communicationStyle: String? = null,
    /** Whether to render MEMORY.md. */
    val includeMemory: Boolean? = null,
)

/** A personality file's presence. */
@Serializable
data class PersonalityFileEntry(
    /** The file name. */
    val filename: String,
    /** Whether it exists. */
    val exists: Boolean,
    /** Its size in bytes. */
    val size: Long,
    /** Its modification time, ms since the epoch. */
    val mtimeMs: Long? = null,
)

/** The personality files of an agent. */
@Serializable
data class PersonalityFiles(
    /** Each editable file. */
    val files: List<PersonalityFileEntry>,
    /** The maximum characters per file. */
    val maxChars: Long,
)

/** A personality file's content. */
@Serializable
data class PersonalityFile(
    /** The file name. */
    val filename: String,
    /** The content (`null` when the file does not exist). */
    val content: String? = null,
    /** Whether it exists. */
    val exists: Boolean,
    /** Whether the content was cut at the size limit. */
    val truncated: Boolean,
    /** Its modification time, for [AgentApi.writePersonality]. */
    val mtimeMs: Long? = null,
)

/** The result of [AgentApi.writePersonality]. */
@Serializable
data class PersonalityWritten(
    /** The number of bytes written. */
    val bytesWritten: Long,
    /** The new modification time. */
    val mtimeMs: Long? = null,
)

/** The rendered default personality files. */
@Serializable
data class PersonalityTemplates(
    /** The preset used. */
    val preset: String,
    /** Each file: `{filename, content}`. */
    val files: List<TemplateFile>,
)

/** The skills an agent loads. */
@Serializable
data class AgentSkills(
    /** The agent. */
    val agent: String,
    /** The loaded skills: name, description, origin (workspace, open-skills, plugin, bundle), editable, … */
    val skills: List<JsonElement>,
    /** The skills left out, with `reason_kind` and `reason`. */
    val dropped: List<JsonElement>? = null,
)

/** A rendered personality file. */
@Serializable
data class TemplateFile(
    /** The file name. */
    val filename: String,
    /** Its content. */
    val content: String,
)

/** One agent's endpoints, from [Client.agent]. */
class AgentApi internal constructor(
    private val c: Client,
    /** The agent's alias. */
    val alias: String,
) {
    private fun workspace(action: String) = listOf("api", "agents", alias, "workspace", action)

    /** `GET /api/agents/{alias}/workspace/list`: a workspace directory, the root without `path`. */
    suspend fun listFiles(path: String? = null): FileListing = c.call("GET", workspace("list"), query = mapOf("path" to path))

    /**
     * `GET /api/agents/{alias}/workspace/read`: a workspace file, up to 4 MiB;
     * binary files come base64-encoded.
     */
    suspend fun readFile(path: String): FileContent = c.call("GET", workspace("read"), query = mapOf("path" to path))

    /** `POST /api/agents/{alias}/workspace/mkdir`: creates a directory. */
    suspend fun makeDir(path: String) = c.empty("POST", workspace("mkdir"), json = buildJsonObject { put("path", path) })

    /** `POST /api/agents/{alias}/workspace/move`: moves or renames a file or directory. */
    suspend fun movePath(
        from: String,
        to: String,
    ) = c.empty(
        "POST",
        workspace("move"),
        json =
            buildJsonObject {
                put("from", from)
                put("to", to)
            },
    )

    /** `DELETE /api/agents/{alias}/workspace/path`: removes a file or directory. */
    suspend fun removePath(path: String) = c.empty("DELETE", workspace("path"), json = buildJsonObject { put("path", path) })

    /** `GET /api/personality`: the agent's personality files and whether each exists. */
    suspend fun personalityFiles(): PersonalityFiles = c.call("GET", listOf("api", "personality"), query = mapOf("agent" to alias))

    /**
     * `GET /api/personality/{filename}`: one personality file. `filename` is
     * SOUL.md, IDENTITY.md, USER.md, AGENTS.md, TOOLS.md, HEARTBEAT.md or
     * MEMORY.md.
     */
    suspend fun personality(filename: String): PersonalityFile =
        c.call("GET", listOf("api", "personality", filename), query = mapOf("agent" to alias))

    /**
     * `PUT /api/personality/{filename}`: replaces a personality file, at most
     * 20 000 characters. With `expectedMtimeMs`, the write is refused (409) if
     * the file changed since it was read.
     */
    suspend fun writePersonality(
        filename: String,
        content: String,
        expectedMtimeMs: Long? = null,
    ): PersonalityWritten =
        c.call(
            "PUT",
            listOf("api", "personality", filename),
            query = mapOf("agent" to alias),
            json =
                buildJsonObject {
                    put("content", content)
                    put("expected_mtime_ms", expectedMtimeMs)
                },
        )

    /** `GET /api/personality/templates`: the default personality files rendered for this agent, without writing them. */
    suspend fun personalityTemplates(context: PersonalityContext = PersonalityContext()): PersonalityTemplates =
        c.call("GET", listOf("api", "personality", "templates"), query = mapOf("agent" to alias) + query(context))

    /**
     * `GET /api/agents/{alias}/skills`: the skills the agent loads, where each
     * comes from, and those the audit dropped.
     */
    suspend fun skills(): AgentSkills = c.call("GET", listOf("api", "agents", alias, "skills"))
}
