package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The answer of `GET /health`. */
@Serializable
data class Health(
    /** Always `ok` while the gateway answers. */
    val status: String,
    /** Whether at least one client is paired. */
    val paired: Boolean,
    /** Whether requests need a bearer token. */
    val requirePairing: Boolean,
    /** The component health (raw when the gateway could not serialize it). */
    val runtime: JsonElement? = null,
)

/** One component's health. */
@Serializable
data class ComponentHealth(
    /** `ok`, `error`, … */
    val status: String,
    /** The last update (RFC 3339). */
    val updatedAt: String,
    /** The last time it was healthy. */
    val lastOk: String? = null,
    /** The last error. */
    val lastError: String? = null,
    /** How many times it was restarted. */
    val restartCount: Long,
)

/** The runtime's component health. */
@Serializable
data class HealthSnapshot(
    /** The daemon process id. */
    val pid: Long,
    /** When it was computed (RFC 3339). */
    val updatedAt: String,
    /** The time since the daemon started, in seconds. */
    val uptimeSeconds: Long,
    /** The health per component (`gateway`, `channel:whatsapp.x`, …). */
    val components: Map<String, ComponentHealth>,
)

/** The daemon's resource use. */
@Serializable
data class ProcessStats(
    /** The resident memory, in bytes. */
    val rssBytes: Long,
    /** The host memory, in bytes. */
    val systemRamTotalBytes: Long,
    /** The CPU use. */
    val cpuPercent: Double? = null,
    /** The number of host CPUs. */
    val numCpus: Long,
)

/** The answer of `GET /api/status`. */
@Serializable
data class Status(
    /** The ZeroClaw version. */
    val version: String,
    /** The model provider in use, as `type.alias`. */
    val modelProvider: String? = null,
    /** The model in use. */
    val model: String,
    /** The sampling temperature. */
    val temperature: Double? = null,
    /** The time since the daemon started, in seconds. */
    val uptimeSeconds: Long,
    /** When the daemon started (RFC 3339). */
    val daemonStartedAt: String,
    /** The gateway port. */
    val gatewayPort: Long,
    /** The UI locale. */
    val locale: String,
    /** The memory backend. */
    val memoryBackend: String,
    /** Whether a client is paired. */
    val paired: Boolean,
    /** The configured channels, as `type.alias`. */
    val channels: Map<String, Boolean>,
    /** The connected nodes and peers found on the network. */
    val nodes: JsonElement? = null,
    /** The component health. */
    val health: HealthSnapshot,
    /** The agent asked about, if any. */
    val agentAlias: String? = null,
    /** The process figures. */
    val process: ProcessStats? = null,
    /** Whether the daemon checks for updates. */
    val checkUpdates: Boolean,
    /** Whether self-upgrade is allowed. */
    val allowSelfUpgrade: Boolean,
    /** `supervised`, `self_respawn` or `manual`. */
    val restartMode: String? = null,
    /** How to restart the daemon. */
    val restartHint: String? = null,
    /** The configuration a running channel picks up without a restart. */
    val liveChannelConfig: List<String>? = null,
)

/** One diagnostic. */
@Serializable
data class Diagnostic(
    /** `ok`, `warn` or `error`. */
    val severity: String,
    /** The area checked. */
    val category: String,
    /** The finding. */
    val message: String,
)

/** The result of [SystemApi.doctor]. */
@Serializable
data class DoctorReport(
    /** Each check. */
    val results: List<Diagnostic>,
    /** The results counted by severity. */
    val summary: DoctorSummary? = null,
)

/** A tool an agent can call. */
@Serializable
data class ToolSpec(
    /** The tool name. */
    val name: String,
    /** What it does. */
    val description: String,
    /** The JSON Schema of its arguments. */
    val parameters: JsonElement? = null,
    /** The JSON Schema of its output, when declared. */
    val output: JsonElement? = null,
    /** The option domain of the parameters that take known values. */
    val paramDomains: Map<String, String>? = null,
)

/** A value a tool parameter accepts. */
@Serializable
data class ToolOption(
    /** The value. */
    val value: String,
    /** The label to show. */
    val label: String? = null,
    /** An extra hint. */
    val hint: String? = null,
)

/** An integration. */
@Serializable
data class Integration(
    /** The integration's name. */
    val name: String,
    /** What it does. */
    val description: String,
    /** `Chat`, `AiModel`, `ToolsAutomation` or `Platform`. */
    val category: String,
    /** The category in words. */
    val categoryLabel: String,
    /** `Available` or `Active`. */
    val status: String,
)

/** An integration's state. */
@Serializable
data class IntegrationSetting(
    /** Whether it is active. */
    val enabled: Boolean,
    /** Its category. */
    val category: String,
    /** Its status. */
    val status: String,
)

/** A command-line tool on the host. */
@Serializable
data class CliTool(
    /** The command name. */
    val name: String,
    /** The full path. */
    val path: String,
    /** The reported version. */
    val version: String? = null,
    /** The kind of tool (`VersionControl`, `Language`, …). */
    val category: String,
)

/** A terminal client connected to the daemon. */
@Serializable
data class Tui(
    /** The client id. */
    val tuiId: String,
    /** When it connected (RFC 3339). */
    val connectedAt: String,
    /** Who it is. */
    val peerLabel: String,
    /** `unix` or `wss`. */
    val transport: String,
)

/** The filters of [SystemApi.logs]; unset, empty and zero values are not sent. */
data class LogQuery(
    /** Keep events at or after (RFC 3339). */
    val sinceTs: String? = null,
    /** Keep events before (RFC 3339). */
    val untilTs: String? = null,
    /** The paging cursor: the previous page's `next_cursor_line_offset`. */
    val untilLineOffset: Long? = null,
    /** The event action. */
    val action: String? = null,
    /** The event category. */
    val category: String? = null,
    /** The event outcome. */
    val outcome: String? = null,
    /** The minimum severity number. */
    val severityMin: Long? = null,
    /** The trace id. */
    val traceId: String? = null,
    /** Free text. */
    val q: String? = null,
    /** Leave out the daemon's internal events. */
    val hideInternal: Boolean? = null,
    /** The page size (gateway default 200). */
    val limit: Long? = null,
    /** Exact matches on attribution fields (`agent_alias`, `channel`, `session_key`, `tool`, …). */
    val attributes: Map<String, String>? = null,
)

/** A page of [SystemApi.logs]. */
@Serializable
data class LogPage(
    /** The log events (OpenTelemetry-like JSON, see ZeroClaw's `LogEvent`). */
    val events: List<JsonElement>,
    /** The cursor for the next (older) page. */
    val nextCursorLineOffset: Long? = null,
    /** Whether this is the oldest page. */
    val atEnd: Boolean,
    /** When the daemon started. */
    val daemonStartedAt: String,
    /** The attribution fields that can be filtered on. */
    val attributionKeys: List<String>? = null,
)

/** The answer of [SystemApi.versionCheck]. */
@Serializable
data class VersionCheck(
    /** The running version. */
    val currentVersion: String,
    /** The latest release (`null` when the check failed). */
    val latestVersion: String? = null,
    /** Whether the latest is newer. */
    val isNewer: Boolean,
    /** The release page. */
    val releaseUrl: String? = null,
    /** The release notes (Markdown). */
    val releaseNotes: String? = null,
    /** The release date. */
    val publishedAt: String? = null,
    /** Why the check failed. */
    val error: String? = null,
)

/** The progress of a self-upgrade. */
@Serializable
data class UpgradeStatus(
    /** `idle`, `running`, `done`, `restarting` or `failed`. */
    val state: String,
    /** The upgrade id. */
    val handoffId: String? = null,
    /** The step, 0 to 6. */
    val phase: Long? = null,
    /** The last log lines. */
    val logTail: List<String>? = null,
    /** The version before. */
    val previousVersion: String? = null,
    /** The version being installed. */
    val targetVersion: String? = null,
    /** How the daemon restarts. */
    val restartMode: String? = null,
    /** How to restart it by hand. */
    val restartHint: String? = null,
    /** Why it failed. */
    val error: String? = null,
)

/** The answer of the `/admin` actions. */
@Serializable
data class AdminResponse(
    /** Whether it was accepted. */
    val success: Boolean,
    /** What happens next. */
    val message: String,
)

/** The doctor's results counted by severity. */
@Serializable
data class DoctorSummary(
    /** Checks that passed. */
    val ok: Long = 0,
    /** Warnings. */
    val warnings: Long = 0,
    /** Errors. */
    val errors: Long = 0,
)

/**
 * Health, status, diagnostics, logs, versions and administration, from
 * [Client.system].
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/lib.rs` (`/health`,
 * `/metrics`, `/admin/ *`), `api.rs`, `api_logs.rs`, `version.rs` and
 * `api_sop_author.rs` (tool parameter options).
 */
class SystemApi internal constructor(
    private val c: Client,
) {
    /** `GET /health`: liveness and component health. Public. */
    suspend fun health(): Health = c.call("GET", listOf("health"))

    /** `GET /api/health`: the runtime's component health. */
    suspend fun runtimeHealth(): HealthSnapshot = c.call("GET", listOf("api", "health"), "health")

    /**
     * `GET /metrics`: the Prometheus text exposition. Public. 404 on a build
     * without the `observability-prometheus` feature.
     */
    suspend fun metrics(): String = c.text("GET", listOf("metrics"))

    /**
     * `GET /api/status`: the version, model, channels, nodes and process
     * figures; with `agent`, that agent's model.
     */
    suspend fun status(agent: String? = null): Status = c.call("GET", listOf("api", "status"), query = mapOf("agent" to agent))

    /** `POST /api/doctor`: runs the configuration diagnostics. */
    suspend fun doctor(): DoctorReport = c.call("POST", listOf("api", "doctor"))

    /** `GET /api/tools`: the tools `agent` (all of them without one) can call. */
    suspend fun tools(agent: String? = null): List<ToolSpec> =
        c.call("GET", listOf("api", "tools"), "tools", query = mapOf("agent" to agent))

    /**
     * `POST /api/tools/param-options`: the values a tool parameter of `domain`
     * accepts. `domain` is `channel_refs`, `peer_targets`, `peer_groups`,
     * `agent_aliases`, `tool_names` or `memory_categories`; `args` holds the
     * sibling arguments the options depend on.
     */
    suspend fun toolParamOptions(
        domain: String,
        agent: String? = null,
        args: JsonElement? = null,
    ): List<ToolOption> =
        c.call(
            "POST",
            listOf("api", "tools", "param-options"),
            "options",
            json =
                buildJsonObject {
                    put("domain", domain)
                    put("agent", agent?.ifEmpty { null })
                    put("args", args ?: JsonNull)
                },
        )

    /** `GET /api/integrations`: the integrations this build offers. */
    suspend fun integrations(): List<Integration> = c.call("GET", listOf("api", "integrations"), "integrations")

    /** `GET /api/integrations/settings`: each integration's state, by name. */
    suspend fun integrationSettings(): Map<String, IntegrationSetting> =
        c.call("GET", listOf("api", "integrations", "settings"), "settings")

    /**
     * `GET /api/cli-tools`: the command-line tools found on the host's PATH.
     * Slow: the gateway runs each with `--version`.
     */
    suspend fun cliTools(): List<CliTool> = c.call("GET", listOf("api", "cli-tools"), "cli_tools")

    /** `GET /api/tuis`: the terminal clients connected to the daemon. */
    suspend fun tuis(): List<Tui> = c.call("GET", listOf("api", "tuis"), "tuis")

    /**
     * `GET /api/logs`: a page of structured log events, newest first.
     * Continue with `untilLineOffset` set to the page's
     * `nextCursorLineOffset`.
     */
    suspend fun logs(filter: LogQuery = LogQuery()): LogPage {
        val query =
            mapOf(
                "since_ts" to filter.sinceTs,
                "until_ts" to filter.untilTs,
                "action" to filter.action,
                "category" to filter.category,
                "outcome" to filter.outcome,
                "trace_id" to filter.traceId,
                "q" to filter.q,
                "until_line_offset" to filter.untilLineOffset,
                "severity_min" to filter.severityMin?.takeIf { it != 0L },
                "limit" to filter.limit?.takeIf { it != 0L },
                "hide_internal" to if (filter.hideInternal == true) true else null,
            ) + filter.attributes.orEmpty()
        return c.call("GET", listOf("api", "logs"), query = query)
    }

    /**
     * `GET /api/version/check`: whether a newer release exists. `force` skips
     * the gateway's one-hour cache; `version` checks that one tag.
     */
    suspend fun versionCheck(
        force: Boolean = false,
        version: String? = null,
    ): VersionCheck =
        c.call(
            "GET",
            listOf("api", "version", "check"),
            query = mapOf("force" to if (force) true else null, "version" to version),
        )

    /**
     * `POST /api/version/upgrade`: starts a self-upgrade to `version` (the
     * latest release without one) and returns its handoff id; follow it with
     * [upgradeStatus]. Needs `gateway.allow_self_upgrade`.
     */
    suspend fun upgrade(
        version: String? = null,
        autoRestart: Boolean = false,
    ): String =
        c.call(
            "POST",
            listOf("api", "version", "upgrade"),
            "handoff_id",
            json =
                buildJsonObject {
                    put("version", version?.ifEmpty { null })
                    put("auto_restart", autoRestart)
                },
        )

    /** `GET /api/version/upgrade/status`: the progress of an upgrade, the latest without `handoffId`. */
    suspend fun upgradeStatus(handoffId: String? = null): UpgradeStatus =
        c.call("GET", listOf("api", "version", "upgrade", "status"), query = mapOf("handoff_id" to handoffId))

    /**
     * `POST /admin/reload`: restarts the daemon's subsystems from the
     * configuration on disk. The connection drops shortly after; reconnect.
     * Allowed from loopback, or remotely with `gateway.allow_remote_admin`.
     */
    suspend fun reload(): AdminResponse = c.call("POST", listOf("admin", "reload"))

    /** `POST /admin/shutdown`: stops the gateway. Loopback only. */
    suspend fun shutdown(): AdminResponse = c.call("POST", listOf("admin", "shutdown"))

    /**
     * `GET /api/openapi.json`: the gateway's OpenAPI document. Public. It
     * covers the configuration, version, SOP and skill routes.
     */
    suspend fun openapi(): JsonElement = c.element("GET", listOf("api", "openapi.json"))
}
