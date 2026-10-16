package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The first-agent quickstart endpoints (`/api/quickstart`), from
 * [Client.quickstart]. Answers are the gateway's JSON, as it evolves with
 * each release.
 */
class QuickstartApi internal constructor(
    private val c: Client,
) {
    /**
     * `GET /api/quickstart/state`: what exists and what can be chosen
     * (provider and channel types, presets, memory kinds, …).
     */
    suspend fun state(): JsonElement = c.element("GET", listOf("api", "quickstart", "state"))

    /**
     * `POST /api/quickstart/fields`: the fields a section of type `typeKey`
     * needs. `section` is `model_provider`, `channel` or `peer_group`.
     */
    suspend fun fields(
        section: String,
        typeKey: String,
    ): JsonElement =
        c.element(
            "POST",
            listOf("api", "quickstart", "fields"),
            json =
                buildJsonObject {
                    put("section", section)
                    put("type_key", typeKey)
                },
        )

    /**
     * `POST /api/quickstart/validate`: checks a submission. Answers
     * `{"kind": "ok"}` or `{"kind": "errors", "errors": […]}`.
     */
    suspend fun validate(submission: JsonElement): JsonElement =
        c.element("POST", listOf("api", "quickstart", "validate"), json = submission)

    /**
     * `POST /api/quickstart/apply`: saves a submission. On
     * `{"kind": "applied", …, "daemon_restarted": true}` the gateway drops
     * connections briefly while it reloads.
     */
    suspend fun apply(submission: JsonElement): JsonElement = c.element("POST", listOf("api", "quickstart", "apply"), json = submission)

    /**
     * `POST /api/quickstart/dismiss`: records that the quickstart was closed.
     * `surface` is `web`, `tui`, `cli` or `test`.
     */
    suspend fun dismiss(
        runId: String,
        surface: String,
        lastStep: String? = null,
    ) = c.empty(
        "POST",
        listOf("api", "quickstart", "dismiss"),
        json =
            buildJsonObject {
                put("run_id", runId)
                put("surface", surface)
                put("last_step", lastStep?.ifEmpty { null })
            },
    )
}
