package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A canvas frame. */
@Serializable
data class CanvasFrame(
    /** The frame id. */
    val frameId: String,
    /** `html`, `svg`, `markdown` or `text`. */
    val contentType: String,
    /** The content. */
    val content: String,
    /** When it was published. */
    val timestamp: String,
)

/**
 * The canvas endpoints (`/api/canvas`), from [Client.canvas]: HTML, SVG,
 * Markdown or text frames an agent (or a client) publishes for display.
 *
 * Gateway handler: `crates/zeroclaw-gateway/src/canvas.rs`. Limits: 100
 * canvases, 50 frames of history each, 256 KiB per frame (the gateway's 64 KiB
 * request limit applies first).
 */
class CanvasApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/canvas`: the canvas ids. */
    suspend fun list(): List<String> = c.call("GET", listOf("api", "canvas"), "canvases")

    /** `GET /api/canvas/{id}`: the current frame. */
    suspend fun current(id: String): CanvasFrame = c.call("GET", listOf("api", "canvas", id), "frame")

    /**
     * `POST /api/canvas/{id}`: publishes a frame, creating the canvas.
     * `contentType` is `html`, `svg`, `markdown` or `text`.
     */
    suspend fun publish(
        id: String,
        contentType: String,
        content: String,
    ): CanvasFrame =
        c.call(
            "POST",
            listOf("api", "canvas", id),
            "frame",
            json =
                buildJsonObject {
                    put("content_type", contentType)
                    put("content", content)
                },
        )

    /** `GET /api/canvas/{id}/history`: past frames, oldest first. */
    suspend fun history(id: String): List<CanvasFrame> = c.call("GET", listOf("api", "canvas", id, "history"), "frames")

    /**
     * `GET /ws/canvas/{id}`: follows a canvas live. Frames are
     * `{"type": "connected" | "frame" | "lagged" | "error", …}`; a `frame`
     * carries the published frame.
     */
    suspend fun watch(id: String): Feed = c.feed(listOf("ws", "canvas", id))

    /** `DELETE /api/canvas/{id}`: clears a canvas. */
    suspend fun clear(id: String) = c.empty("DELETE", listOf("api", "canvas", id))
}
