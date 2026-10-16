package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.time.Duration.Companion.seconds

/** How long a manual run may take: the gateway allows 600 s. */
val CRON_LONG_RUN = 660.seconds

/** When a job runs, told apart by `kind`. */
@Serializable
@JsonClassDiscriminator("kind")
sealed class Schedule {
    /** On a cron expression, in a time zone (UTC when unset). */
    @Serializable
    @SerialName("cron")
    data class Cron(
        val expr: String,
        val tz: String? = null,
    ) : Schedule()

    /** Once, at a time (RFC 3339). */
    @Serializable
    @SerialName("at")
    data class At(
        val at: String,
    ) : Schedule()

    /** Every `everyMs` milliseconds. */
    @Serializable
    @SerialName("every")
    data class Every(
        val everyMs: Long,
    ) : Schedule()
}

/** A delivery that posts the output to `to` on `channel` (`type.alias`). */
fun announce(
    channel: String,
    to: String,
): Delivery = Delivery(mode = "announce", channel = channel, to = to, bestEffort = true)

/** Where a job's output goes. */
@Serializable
data class Delivery(
    /** `none` or `announce` (post it on a channel). */
    val mode: String,
    /** The channel, as `type.alias`. */
    val channel: String? = null,
    /** The recipient on the channel. */
    val to: String? = null,
    /** The thread to post in. */
    val threadId: String? = null,
    /** Whether a delivery failure leaves the run successful. */
    val bestEffort: Boolean? = null,
)

/** A scheduled job. */
@Serializable
data class CronJob(
    /** The job id. */
    val id: String,
    /** The schedule expression as written. */
    val expression: String,
    /** The parsed schedule. */
    val schedule: Schedule,
    /** The shell command (shell jobs). */
    val command: String? = null,
    /** The prompt (agent jobs). */
    val prompt: String? = null,
    /** The name. */
    val name: String? = null,
    /** `shell` or `agent`. */
    val jobType: String,
    /** `isolated` or `main`. */
    val sessionTarget: String,
    /** The model override (agent jobs). */
    val model: String? = null,
    /** The agent that owns it. */
    val agentAlias: String,
    /** Whether it runs. */
    val enabled: Boolean,
    /** Where its output goes. */
    val delivery: Delivery,
    /** Whether it is deleted after one run. */
    val deleteAfterRun: Boolean,
    /** The tools an agent job may use (`null`: no restriction). */
    val allowedTools: List<String>? = null,
    /** Whether an agent job uses memory. */
    val usesMemory: Boolean? = null,
    /** `imperative` (API, CLI) or `declarative` (config file). */
    val source: String? = null,
    /** `wrapped` or `raw` (shell jobs). */
    val shellOutputFormat: String? = null,
    /** The creation time (RFC 3339). */
    val createdAt: String,
    /** The next run (RFC 3339). */
    val nextRun: String,
    /** The last run (RFC 3339). */
    val lastRun: String? = null,
    /** The last status. */
    val lastStatus: String? = null,
    /** The last output. */
    val lastOutput: String? = null,
)

/**
 * A job to create. Shell jobs need `command`; agent jobs `prompt` (and
 * `job_type` `agent`, implied by a prompt). Unset fields are left out.
 */
@Serializable
data class NewCronJob(
    /** The owning agent (must be configured). */
    val agent: String,
    /** The cron expression. */
    val schedule: String,
    /** The name. */
    val name: String? = null,
    /** The IANA time zone of the schedule. */
    val tz: String? = null,
    /** The shell command. */
    val command: String? = null,
    /** `shell` or `agent`. */
    val jobType: String? = null,
    /** The agent prompt. */
    val prompt: String? = null,
    /** Where the output goes. */
    val delivery: Delivery? = null,
    /** `isolated` (default) or `main`. */
    val sessionTarget: String? = null,
    /** The model override. */
    val model: String? = null,
    /** The tools an agent job may use (unset: no restriction; `[]`: none). */
    val allowedTools: List<String>? = null,
    /** Delete the job after its first run. */
    val deleteAfterRun: Boolean? = null,
    /** Whether an agent job uses memory (default true). */
    val usesMemory: Boolean? = null,
    /** `wrapped` (default) or `raw`: how a shell job's output is announced. */
    val shellOutputFormat: String? = null,
)

/** Changes to a job; unset fields stay as they are. */
@Serializable
data class CronJobPatch(
    /** The owning agent; required when changing a shell job's command. */
    val agent: String? = null,
    /** The name. */
    val name: String? = null,
    /** The cron expression. */
    val schedule: String? = null,
    /** The IANA time zone. */
    val tz: String? = null,
    /** Remove the time zone. */
    val clearTz: Boolean? = null,
    /** The shell command (or an agent job's prompt). */
    val command: String? = null,
    /** The agent prompt. */
    val prompt: String? = null,
    /** Pause (false) or resume the job. */
    val enabled: Boolean? = null,
    /** Whether an agent job uses memory. */
    val usesMemory: Boolean? = null,
    /** `wrapped` or `raw`. */
    val shellOutputFormat: String? = null,
)

/** The result of [CronApi.run]. */
@Serializable
data class CronRunResult(
    /** The run status. */
    val status: String,
    /** The job id. */
    val jobId: String,
    /** Whether the job succeeded. */
    val success: Boolean,
    /** Its output. */
    val output: String,
    /** The duration, in milliseconds. */
    val durationMs: Double,
    /** The start (RFC 3339). */
    val startedAt: String,
    /** The end (RFC 3339). */
    val finishedAt: String,
)

/** A recorded run. */
@Serializable
data class CronRun(
    /** The run id. */
    val id: Long,
    /** The job id. */
    val jobId: String,
    /** The start (RFC 3339). */
    val startedAt: String,
    /** The end (RFC 3339). */
    val finishedAt: String,
    /** The status. */
    val status: String,
    /** The output. */
    val output: String? = null,
    /** The duration, in milliseconds. */
    val durationMs: Double? = null,
)

/** The scheduler settings. */
@Serializable
data class CronSettings(
    /** Whether the scheduler runs jobs. */
    val enabled: Boolean,
    /** Whether missed runs run at startup. */
    val catchUpOnStartup: Boolean,
    /** The number of runs kept per job. */
    val maxRunHistory: Long,
)

/**
 * The scheduled jobs endpoints (`/api/cron`), from [Client.cron]: shell
 * commands or agent prompts on a cron schedule, their results delivered to a
 * channel.
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/api.rs` (`/api/cron*`);
 * types in `crates/zeroclaw-runtime/src/cron/types.rs`.
 */
class CronApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/cron`: every job. */
    suspend fun list(): List<CronJob> = c.call("GET", listOf("api", "cron"), "jobs")

    /** `POST /api/cron`: creates a job. */
    suspend fun create(job: NewCronJob): CronJob = c.call("POST", listOf("api", "cron"), "job", json = body(job))

    /** `PATCH /api/cron/{id}`: changes a job; unset fields stay as they are. */
    suspend fun update(
        id: String,
        patch: CronJobPatch,
    ): CronJob = c.call("PATCH", listOf("api", "cron", id), "job", json = body(patch))

    /** `DELETE /api/cron/{id}`: deletes a job. */
    suspend fun delete(id: String) = c.empty("DELETE", listOf("api", "cron", id))

    /**
     * `POST /api/cron/{id}/run`: runs a job now and waits for it. The gateway
     * allows up to its long-running timeout, 600 s by default. A failed job
     * still throws nothing, with `success` false.
     */
    suspend fun run(id: String): CronRunResult = c.call("POST", listOf("api", "cron", id, "run"), timeout = CRON_LONG_RUN)

    /** `GET /api/cron/{id}/runs`: the job's latest runs; `limit` is 1 to 100 (gateway default 20). */
    suspend fun runs(
        id: String,
        limit: Int? = null,
    ): List<CronRun> = c.call("GET", listOf("api", "cron", id, "runs"), "runs", query = mapOf("limit" to limit))

    /** `GET /api/cron/settings`: the scheduler settings. */
    suspend fun settings(): CronSettings = c.call("GET", listOf("api", "cron", "settings"))

    /** `PATCH /api/cron/settings`: changes the scheduler settings that are set; saved to the configuration. */
    suspend fun updateSettings(settings: CronSettingsPatch): CronSettings =
        c.call("PATCH", listOf("api", "cron", "settings"), json = body(settings))
}

/** Changes to the scheduler settings; unset fields stay as they are. */
@Serializable
data class CronSettingsPatch(
    /** Whether the scheduler runs jobs. */
    val enabled: Boolean? = null,
    /** Whether missed runs run at startup. */
    val catchUpOnStartup: Boolean? = null,
    /** The number of runs kept per job. */
    val maxRunHistory: Long? = null,
)
