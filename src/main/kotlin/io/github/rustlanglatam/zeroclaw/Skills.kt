package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A skill bundle. */
@Serializable
data class SkillBundle(
    /** The bundle's alias. */
    val alias: String,
    /** Its directory. */
    val directory: String,
    /** The skills it includes. */
    val include: List<String>? = null,
    /** The skills it leaves out. */
    val exclude: List<String>? = null,
)

/** A skill in a listing. */
@Serializable
data class SkillSummary(
    /** The bundle. */
    val bundle: String,
    /** The skill's name. */
    val name: String,
    /** Its directory. */
    val directory: String,
    /** The front matter (name, description, tags, slash_options, …). */
    val frontmatter: JsonElement? = null,
)

/** A skill. */
@Serializable
data class Skill(
    /** The bundle. */
    val bundle: String,
    /** The skill's name. */
    val name: String,
    /** The front matter. */
    val frontmatter: JsonElement? = null,
    /** The Markdown body. */
    val body: String,
)

/** The skill bundles and skills (`/api/skills`), from [Client.skills]. */
class SkillsApi internal constructor(
    private val c: Client,
) {
    private fun skillPath(
        bundle: String,
        name: String,
    ) = listOf("api", "skills", "bundles", bundle, "skills", name)

    /** `GET /api/skills/bundles`: the skill bundles. */
    suspend fun bundles(): List<SkillBundle> = c.call("GET", listOf("api", "skills", "bundles"), "bundles")

    /** `GET /api/skills/bundles/{bundle}/skills`: the skills of a bundle. */
    suspend fun list(bundle: String): List<SkillSummary> = c.call("GET", listOf("api", "skills", "bundles", bundle, "skills"), "skills")

    /**
     * `POST /api/skills/bundles/{bundle}/skills`: creates a skill. It gets
     * scripts/, references/ and assets/ unless `noScaffold`.
     */
    suspend fun create(
        bundle: String,
        name: String,
        frontmatter: JsonElement,
        body: String,
        noScaffold: Boolean = false,
    ) = c.empty(
        "POST",
        listOf("api", "skills", "bundles", bundle, "skills"),
        json =
            buildJsonObject {
                put("name", name)
                put("frontmatter", frontmatter)
                put("body", body)
                put("no_scaffold", noScaffold)
            },
    )

    /** `GET /api/skills/bundles/{bundle}/skills/{name}`: a skill. */
    suspend fun read(
        bundle: String,
        name: String,
    ): Skill = c.call("GET", skillPath(bundle, name))

    /** `PUT /api/skills/bundles/{bundle}/skills/{name}`: replaces a skill's front matter and body. */
    suspend fun write(
        bundle: String,
        name: String,
        frontmatter: JsonElement,
        body: String,
    ) = c.empty(
        "PUT",
        skillPath(bundle, name),
        json =
            buildJsonObject {
                put("frontmatter", frontmatter)
                put("body", body)
            },
    )

    /** `DELETE /api/skills/bundles/{bundle}/skills/{name}`: archives a skill, or deletes it for good with `purge`. */
    suspend fun delete(
        bundle: String,
        name: String,
        purge: Boolean = false,
    ) = c.empty("DELETE", skillPath(bundle, name), query = mapOf("purge" to purge))

    /** `GET /api/skills/slash-option-kinds`: the option types a skill's slash command may declare. */
    suspend fun slashOptionKinds(): List<JsonElement> = c.call("GET", listOf("api", "skills", "slash-option-kinds"), "kinds")
}
