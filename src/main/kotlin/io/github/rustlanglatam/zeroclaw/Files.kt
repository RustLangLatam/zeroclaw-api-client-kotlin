package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** A directory entry. */
@Serializable
data class FileEntry(
    /** The entry's name. */
    val name: String,
    /** `dir` or `file`. */
    val kind: String,
    /** The size of a file. */
    val size: Long? = null,
    /** Whether it may not be changed. */
    val protected: Boolean? = null,
)

/** A directory's entries. */
@Serializable
data class FileListing(
    /** The directory. */
    val path: String,
    /** The directories first, then the files, alphabetically. */
    val entries: List<FileEntry>,
)

/** A file's content. */
@Serializable
data class FileContent(
    /** The file. */
    val path: String,
    /** Its size in bytes. */
    val size: Long,
    /** Whether it is text. */
    val isText: Boolean,
    /** The content, in `encoding`. */
    val content: String,
    /** `utf8` or `base64`. */
    val encoding: String,
)

/** A stored upload. */
@Serializable
data class Upload(
    /** Where it was stored. */
    val path: String,
    /** `[IMAGE:<path>]`, to put in a message. */
    val marker: String,
)

/** The shared-directory and upload endpoints, from [Client.files]. */
class FilesApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/browse`: a shared-area directory, its root without `path`. */
    suspend fun list(path: String? = null): FileListing = c.call("GET", listOf("api", "browse"), query = mapOf("path" to path))

    /** `POST /api/browse/mkdir`: creates a directory. */
    suspend fun makeDir(path: String) = c.empty("POST", listOf("api", "browse", "mkdir"), json = buildJsonObject { put("path", path) })

    /** `DELETE /api/browse/rmdir`: removes a directory and its contents. */
    suspend fun removeDir(path: String) = c.empty("DELETE", listOf("api", "browse", "rmdir"), json = buildJsonObject { put("path", path) })

    /**
     * `POST /api/upload`: stores an image in `agent`'s workspace. PNG, JPEG,
     * GIF or WebP, told by its bytes. Returns the marker that attaches it to a
     * chat message.
     */
    suspend fun uploadImage(
        agent: String,
        image: ByteArray,
    ): Upload =
        c.call(
            "POST",
            listOf("api", "upload"),
            query = mapOf("agent" to agent),
            body = image.toRequestBody("application/octet-stream".toMediaType()),
        )
}
