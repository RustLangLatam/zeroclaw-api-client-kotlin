package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * An answer to a waiting SOP run, encoded externally tagged as the gateway
 * expects. Build it with the companion's functions.
 */
class SopDecision private constructor(
    /** The decision as sent. */
    val json: JsonElement,
) {
    override fun toString() = json.toString()

    companion object {
        /** Go on. */
        @JvmStatic
        fun approve() = SopDecision(JsonPrimitive("approve"))

        /** Stop the run; `reason` may be empty. */
        @JvmStatic
        @JvmOverloads
        fun deny(reason: String = "") =
            SopDecision(
                buildJsonObject {
                    putJsonObject("deny") { if (reason.isNotEmpty()) put("reason", reason) }
                },
            )

        /** Change the checkpoint's text and go on. */
        @JvmStatic
        fun amend(text: String) = SopDecision(buildJsonObject { putJsonObject("amend") { put("text", text) } })

        /** Redo the step with `guidance`. */
        @JvmStatic
        fun revise(guidance: String) = SopDecision(buildJsonObject { putJsonObject("revise") { put("guidance", guidance) } })
    }
}

/** A run in a listing. */
@Serializable
data class SopRun(
    /** The run id. */
    val runId: String,
    /** The SOP. */
    val sopName: String,
    /**
     * `pending`, `running`, `cancel_requested`, `waiting_approval`,
     * `paused_checkpoint`, `completed`, `failed` or `cancelled`.
     */
    val status: String,
    /** The current step. */
    val currentStep: Long,
    /** The number of steps in all. */
    val totalSteps: Long,
    /** The start (RFC 3339). */
    val startedAt: String,
    /** The end. */
    val completedAt: String? = null,
    /** What started it. */
    val triggerSource: String,
    /** Whether it is still running. */
    val active: Boolean,
)

/** A run waiting for an operator. */
@Serializable
data class PendingApproval(
    /** The run id. */
    val runId: String,
    /** The SOP. */
    val sopName: String,
    /** The step waiting. */
    val step: Long,
    /** The number of steps in all. */
    val totalSteps: Long,
    /** Since when. */
    val waitingSince: String? = null,
    /** `approval` or `checkpoint`. */
    val kind: String,
)

/**
 * The standard operating procedure endpoints (`/api/sops`), from
 * [Client.sops]: authoring, graphs, runs, approvals and cancellation.
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/api_sop_author.rs` and
 * `api_sop.rs`. An SOP definition (name, description, version, priority,
 * execution_mode, triggers, steps, …) is large and versioned with ZeroClaw,
 * so it travels as decoded JSON; so do graphs and run overlays. Every route
 * answers 503 while the SOP subsystem is disabled.
 */
class SopsApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/sops`: every SOP definition that loads. */
    suspend fun list(): List<JsonElement> = c.call("GET", listOf("api", "sops"), "sops")

    /** `GET /api/sops/{name}/full`: one SOP definition. */
    suspend fun get(name: String): JsonElement = c.element("GET", listOf("api", "sops", name, "full"))

    /** `POST /api/sops`: creates an SOP, 409 when the name exists. */
    suspend fun create(sop: JsonElement) = c.empty("POST", listOf("api", "sops"), json = sop)

    /** `PUT /api/sops/{name}`: saves an SOP; steps are renumbered 1..N. */
    suspend fun save(
        name: String,
        sop: JsonElement,
    ) = c.empty("PUT", listOf("api", "sops", name), json = sop)

    /** `DELETE /api/sops/{name}`: deletes an SOP. */
    suspend fun delete(name: String) = c.empty("DELETE", listOf("api", "sops", name))

    /** `GET /api/sops/{name}/graph`: the SOP as a node graph, with layout and diagnostics. */
    suspend fun graph(name: String): JsonElement = c.element("GET", listOf("api", "sops", name, "graph"))

    /** `POST /api/sops/graph-draft`: the graph of an unsaved definition. */
    suspend fun graphDraft(sop: JsonElement): JsonElement =
        c.element("POST", listOf("api", "sops", "graph-draft"), json = buildJsonObject { put("sop", sop) })

    /**
     * `POST /api/sops/wire-draft`: applies a wiring edit to an unsaved
     * definition. `edit` is `{op: "connect" | "disconnect", from, to, role,
     * port?}`. Returns `{sop, graph}`.
     */
    suspend fun wireDraft(
        sop: JsonElement,
        edit: JsonElement,
    ): JsonElement =
        c.element(
            "POST",
            listOf("api", "sops", "wire-draft"),
            json =
                buildJsonObject {
                    put("sop", sop)
                    put("edit", edit)
                },
        )

    /** `GET /api/sops/graph-legend`: what the graph's roles, pins and run states mean. */
    suspend fun graphLegend(): JsonElement = c.element("GET", listOf("api", "sops", "graph-legend"))

    /** `GET /api/sops/trigger-sources`: the trigger sources, their fields and the channels that can start an SOP. */
    suspend fun triggerSources(): JsonElement = c.element("GET", listOf("api", "sops", "trigger-sources"))

    /**
     * `POST /api/sops/{name}/run`: starts the SOP through its manual trigger.
     * `payload` is JSON text handed to the run. Returns the run id (an
     * existing run's when the SOP coalesces).
     */
    suspend fun run(
        name: String,
        payload: String? = null,
    ): String =
        c.call(
            "POST",
            listOf("api", "sops", name, "run"),
            "run_id",
            json = buildJsonObject { put("payload", payload?.ifEmpty { null }) },
        )

    /** `GET /api/sops/runs`: active and recent runs, newest first, of one SOP when `sop` is set. */
    suspend fun runs(sop: String? = null): List<SopRun> = c.call("GET", listOf("api", "sops", "runs"), "runs", query = mapOf("sop" to sop))

    /**
     * `GET /ws/sops/runs`: follows SOP runs live. A
     * `{"type": "snapshot", "runs": […]}` first, then
     * `{"type": "run", "run": …}` on every change (lagged, disabled and error
     * frames too).
     */
    suspend fun watchRuns(): Feed = c.feed(listOf("ws", "sops", "runs"))

    /** `GET /api/sops/{name}/runs/{run_id}/overlay`: a run's state per step, with its tool calls. */
    suspend fun runOverlay(
        name: String,
        runId: String,
    ): JsonElement = c.element("GET", listOf("api", "sops", name, "runs", runId, "overlay"))

    /**
     * `POST /api/sops/{name}/runs/{run_id}/decide`: answers a run waiting for
     * approval or at a checkpoint; returns the run overlay.
     */
    suspend fun decide(
        name: String,
        runId: String,
        decision: SopDecision,
    ): JsonElement = c.element("POST", listOf("api", "sops", name, "runs", runId, "decide"), json = decision.json)

    /** `POST /api/sops/{name}/runs/{run_id}/cancel`: cancels a run at its next step boundary. */
    suspend fun cancel(
        name: String,
        runId: String,
        reason: String? = null,
    ): JsonElement =
        c.element(
            "POST",
            listOf("api", "sops", name, "runs", runId, "cancel"),
            json = buildJsonObject { put("reason", reason?.ifEmpty { null }) },
        )

    /** `GET /admin/sop/pending`: the runs waiting for an approval or a checkpoint. Loopback, or remote admin with pairing. */
    suspend fun pending(): List<PendingApproval> = c.call("GET", listOf("admin", "sop", "pending"), "pending")

    /**
     * `POST /admin/sop/approve`: approves a waiting run. Returns the outcome
     * (`resumed`, `already_resolved`, `pending_quorum (…)`, …).
     */
    suspend fun approve(runId: String): String = resolve("approve", runId, null)

    /** `POST /admin/sop/deny`: denies a waiting run; returns the outcome. */
    suspend fun deny(
        runId: String,
        reason: String? = null,
    ): String = resolve("deny", runId, reason)

    private suspend fun resolve(
        action: String,
        runId: String,
        reason: String?,
    ): String =
        c.call(
            "POST",
            listOf("admin", "sop", action),
            "outcome",
            json =
                buildJsonObject {
                    put("run_id", runId)
                    put("reason", reason?.ifEmpty { null })
                },
        )
}
