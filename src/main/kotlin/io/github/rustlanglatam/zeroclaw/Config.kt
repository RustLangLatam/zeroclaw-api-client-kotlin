package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The header that lets [ConfigApi.patch] overwrite paths changed on disk since the gateway loaded them. */
const val OVERRIDE_DRIFT_HEADER = "X-ZeroClaw-Override-Drift"

/** Whether a [PropValue] is a secret (only `populated` is known). */
val PropValue.isSecret: Boolean get() = populated != null

/** A validation problem the gateway reports without refusing the write. */
@Serializable
data class ValidationWarning(
    /** Stable, e.g. `pre_existing_validation_error`. */
    val code: String,
    /** What is wrong. */
    val message: String,
    /** The field concerned. */
    val path: String,
)

/**
 * A field as GET, PUT and DELETE `/api/config/prop` return it. A regular
 * field carries `value`, a secret only `populated`.
 */
@Serializable
data class PropValue(
    /** The dotted path. */
    val path: String,
    /** The gateway's display value (`null` after a reset, and absent for secrets). */
    val value: JsonElement? = null,
    /** Validation problems of the configuration. */
    val warnings: List<ValidationWarning>? = null,
    /** For a secret, whether it has a value. */
    val populated: Boolean? = null,
)

/** What one patch operation left. */
@Serializable
data class PatchOpResult(
    /** The operation. */
    val op: String,
    /** The dotted path. */
    val path: String,
    /** The new display value (absent for secrets and comments). */
    val value: JsonElement? = null,
    /** For secrets, whether it now has a value. */
    val populated: Boolean? = null,
    /** For `comment`, the comment written. */
    val comment: String? = null,
)

/** The result of [ConfigApi.patch]. */
@Serializable
data class PatchResponse(
    /** Always true on success: the patch was saved. */
    val saved: Boolean,
    /** One result per operation, in order. */
    val results: List<PatchOpResult>,
    /** Validation problems outside the patched paths. */
    val warnings: List<ValidationWarning>? = null,
)

/** A field whose value on disk differs from the gateway's. */
@Serializable
data class DriftEntry(
    /** The dotted path. */
    val path: String,
    /** Whether it is a secret (no values shown). */
    val secret: Boolean? = null,
    /** Always true. */
    val drifted: Boolean,
    /** The gateway's value. */
    val inMemoryValue: JsonElement? = null,
    /** The value in `config.toml`. */
    val onDiskValue: JsonElement? = null,
)

/** One configuration field. */
@Serializable
data class ConfigEntry(
    /** The dotted path. */
    val path: String,
    /** Groups fields. */
    val category: String,
    /**
     * `string`, `bool`, `integer`, `float`, `enum`, `alias-ref`,
     * `string-array`, `object-array` or `object`.
     */
    val kind: String,
    /** The Rust type, e.g. `Option<String>`. */
    val typeHint: String,
    /** The display value; absent for secrets. */
    val value: JsonElement? = null,
    /** Whether it has a value. */
    val populated: Boolean,
    /** Whether it is a secret. */
    val isSecret: Boolean,
    /** Whether an environment variable overrides it. */
    val isEnvOverridden: Boolean? = null,
    /** The allowed values of an enum. */
    val enumVariants: List<String>? = null,
    /** The setup section it belongs to. */
    val section: String? = null,
    /** The dashboard tab. */
    val tab: String? = null,
    /** Whether it is edited as multi-line text. */
    val multiline: Boolean? = null,
)

/** The result of [ConfigApi.list]. */
@Serializable
data class ConfigList(
    /** The fields. */
    val entries: List<ConfigEntry>,
    /** The fields whose value on disk differs from the gateway's. */
    val drifted: List<DriftEntry>? = null,
)

/** The result of [ConfigApi.migrate]. */
@Serializable
data class MigrateResponse(
    /** Whether the file changed. */
    val migrated: Boolean,
    /** Where the previous file was kept. */
    val backupPath: String? = null,
    /** The schema version now on disk. */
    val schemaVersion: Long,
)

/** A map or list section and the type of its entries. */
@Serializable
data class TemplateEntry(
    /** The dotted path. */
    val path: String,
    /** `map` or `list`. */
    val kind: String,
    /** The Rust type of an entry. */
    val valueType: String,
    /** What the section holds. */
    val description: String,
)

/** The result of creating or deleting a map alias. */
@Serializable
data class MapKeyResponse(
    /** The map section. */
    val path: String,
    /** The alias. */
    val key: String,
    /** Whether a new alias was created. */
    val created: Boolean,
    /** Cleanup steps that failed after the change was saved. */
    val warnings: List<String>? = null,
)

/** A field that references an alias. */
@Serializable
data class ReferenceSite(
    /** The dotted path of the referencing field. */
    val path: String,
    /** Its raw value. */
    val rawValue: String,
)

/** What deleting an alias would do. */
@Serializable
data class DeletePlan(
    /** The map section. */
    val path: String,
    /** The alias. */
    val key: String,
    /** Whether the delete would go through. */
    val allowed: Boolean,
    /** The references that block it. */
    val blockers: List<ReferenceSite>,
    /** The references it would clear. */
    val scrubs: List<ReferenceSite>,
    /** For agents, the live ACP sessions (they block the delete). */
    val liveAcpSessions: Long? = null,
    /** Whether the agent's memory, cron jobs and sessions go too. */
    val cascadesOwnedState: Boolean,
)

/** The result of [ConfigApi.renameMapKey]. */
@Serializable
data class RenameResponse(
    /** The map section. */
    val path: String,
    /** The old alias. */
    val from: String,
    /** The new alias. */
    val to: String,
    /** Whether it was renamed. */
    val renamed: Boolean,
    /** Follow-up steps that failed (re-sending the rename finishes them). */
    val warnings: List<String>? = null,
)

/** A model provider the build knows. */
@Serializable
data class CatalogModelProvider(
    /** The provider family name. */
    val name: String,
    /** Shown to people. */
    val displayName: String,
    /** Whether it runs locally. */
    val local: Boolean,
)

/** A model's prices as the provider states them. */
@Serializable
data class ModelPricing(
    /** The input price. */
    val prompt: String? = null,
    /** The output price. */
    val completion: String? = null,
    /** The cached input read price. */
    val inputCacheRead: String? = null,
    /** The cached input write price. */
    val inputCacheWrite: String? = null,
)

/** The models of a provider. */
@Serializable
data class CatalogModels(
    /** The provider family name. */
    val modelProvider: String,
    /** The model ids. */
    val models: List<String>,
    /** The prices per model, when the provider publishes them. */
    val pricing: Map<String, ModelPricing>? = null,
    /** Whether it runs locally. */
    val local: Boolean,
    /** Whether the list came live from the provider. */
    val live: Boolean,
)

/** Whether the setup is complete. */
@Serializable
data class ConfigStatus(
    /** Whether the quickstart still has to run. */
    val needsQuickstart: Boolean,
    /** `has_dispatchable_agent`, `incomplete_agent` or `fresh_install`. */
    val reason: String,
    /** Whether part of the setup is done. */
    val hasPartialState: Boolean,
    /** The steps left, in words. */
    val missing: List<String>,
)

/** The aliases an agent can reference. */
@Serializable
data class AgentOptions(
    /** Channels, as `type.alias`. */
    val channels: List<String>,
    /** The channel types. */
    val channelTypes: List<String>,
    /** Model providers, as `type.alias`. */
    val modelProviders: List<String>,
    /** The risk profiles. */
    val riskProfiles: List<String>,
    /** The runtime profiles. */
    val runtimeProfiles: List<String>,
    /** The skill bundles. */
    val skillBundles: List<String>,
    /** The knowledge bundles. */
    val knowledgeBundles: List<String>,
    /** The MCP bundles. */
    val mcpBundles: List<String>,
    /** The agents. */
    val agents: List<String>,
)

/** A setup section. */
@Serializable
data class ConfigSection(
    /** The section key, e.g. `providers.models`. */
    val key: String,
    /** Shown to people. */
    val label: String,
    /** The help text. */
    val help: String,
    /** Whether it offers items to pick ([ConfigApi.sectionItems]). */
    val hasPicker: Boolean,
    /** Whether it is completed. */
    val completed: Boolean,
    /** Whether it is ready to use. */
    val ready: Boolean? = null,
    /** The group it is shown under. */
    val group: String? = null,
    /** Whether the quickstart includes it. */
    val isQuickstart: Boolean? = null,
    /** `direct_form`, `one_tier_alias_map`, `typed_family_map` or `backend_picker`. */
    val shape: String? = null,
    /** The cost category. */
    val costCategory: String? = null,
)

/** An item of a picker section. */
@Serializable
data class SectionItem(
    /** The item key. */
    val key: String,
    /** Shown to people. */
    val label: String,
    /** What it is. */
    val description: String? = null,
    /** `configured`, `needs setup`, `active` or `created`. */
    val badge: String? = null,
)

/** The items of a picker section. */
@Serializable
data class SectionItems(
    /** The section key. */
    val section: String,
    /** The items. */
    val items: List<SectionItem>,
    /** The help text. */
    val help: String,
)

/** The result of [ConfigApi.selectSectionItem]. */
@Serializable
data class SelectedItem(
    /** The prefix of the fields to fill ([ConfigApi.list]). */
    val fieldsPrefix: String,
    /** Whether something was created. */
    val created: Boolean,
)

/**
 * One operation of [ConfigApi.patch]: RFC 6902 `add`, `replace`, `remove` and
 * `test`, plus ZeroClaw's `comment`. Build it with the companion's functions.
 */
@Serializable
data class PatchOp(
    /** `add`, `replace`, `remove`, `test` or `comment`. */
    val op: String,
    /** The dotted path. */
    val path: String,
    /** The value (`add`, `replace`, `test`). */
    val value: JsonElement? = null,
    /** The comment (`comment`). */
    val comment: String? = null,
) {
    companion object {
        /** Replaces the value at `path`. */
        @JvmStatic
        fun replace(
            path: String,
            value: JsonElement,
        ) = PatchOp("replace", path, value)

        /** Adds the value at `path`. */
        @JvmStatic
        fun add(
            path: String,
            value: JsonElement,
        ) = PatchOp("add", path, value)

        /** Removes `path`. */
        @JvmStatic
        fun remove(path: String) = PatchOp("remove", path)

        /** Fails the whole patch unless `path` holds `value`. */
        @JvmStatic
        fun test(
            path: String,
            value: JsonElement,
        ) = PatchOp("test", path, value)

        /** Writes `comment` above the key of `path` in the TOML. */
        @JvmStatic
        fun comment(
            path: String,
            comment: String,
        ) = PatchOp("comment", path, comment = comment)
    }
}

/**
 * The configuration endpoints (`/api/config`), from [Client.config]:
 * per-property reads and writes, atomic JSON Patch batches, map aliases
 * (agents, providers, channels…), drift, catalogs and the setup sections.
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/api_config.rs` and
 * `api_sections.rs`. Property paths are dotted
 * (`providers.models.openai.default.api_key`). Reads return values as the
 * gateway's display strings; writes take typed JSON, which the gateway
 * coerces to the field's kind. Secrets are never returned: their reads say
 * only whether they are populated.
 */
class ConfigApi internal constructor(
    private val c: Client,
) {
    /**
     * `GET /api/config`: the whole configuration with secrets masked as
     * `***MASKED***`. A compatibility snapshot; prefer [list] and [get], which
     * carry field metadata.
     */
    suspend fun snapshot(): JsonElement = c.element("GET", listOf("api", "config"))

    /** `OPTIONS /api/config`: the JSON Schema of the whole configuration. Static per gateway build. Public. */
    suspend fun schema(): JsonElement = c.element("OPTIONS", listOf("api", "config"))

    /** `OPTIONS /api/config/prop`: the schema plus the field's metadata under `x-zeroclaw-prop`. Public. */
    suspend fun propSchema(path: String): JsonElement = c.element("OPTIONS", listOf("api", "config", "prop"), query = mapOf("path" to path))

    /** `GET /api/config/prop`: reads one field. */
    suspend fun get(path: String): PropValue = c.call("GET", listOf("api", "config", "prop"), query = mapOf("path" to path))

    /**
     * `PUT /api/config/prop`: writes one field. `value` is sent as JSON
     * ([JsonNull] resets it); a non-empty `comment` is written above the key
     * in the TOML. A map alias implied by the path is created.
     */
    suspend fun set(
        path: String,
        value: JsonElement,
        comment: String? = null,
    ): PropValue =
        c.call(
            "PUT",
            listOf("api", "config", "prop"),
            json =
                buildJsonObject {
                    put("path", path)
                    put("value", value)
                    if (!comment.isNullOrEmpty()) put("comment", comment)
                },
        )

    /** `PUT /api/config/prop`: writes a text field; see [set]. */
    suspend fun set(
        path: String,
        value: String,
        comment: String? = null,
    ): PropValue = set(path, JsonPrimitive(value), comment)

    /** `PUT /api/config/prop`: writes a number field; see [set]. */
    suspend fun set(
        path: String,
        value: Number,
        comment: String? = null,
    ): PropValue = set(path, JsonPrimitive(value), comment)

    /** `PUT /api/config/prop`: writes a boolean field; see [set]. */
    suspend fun set(
        path: String,
        value: Boolean,
        comment: String? = null,
    ): PropValue = set(path, JsonPrimitive(value), comment)

    /** `DELETE /api/config/prop`: resets one field to its default. */
    suspend fun reset(path: String): PropValue = c.call("DELETE", listOf("api", "config", "prop"), query = mapOf("path" to path))

    /**
     * `PATCH /api/config`: applies `ops` atomically, all or nothing. With
     * `overrideDrift`, paths edited on disk since the gateway loaded them are
     * overwritten instead of refused with 409.
     */
    suspend fun patch(
        ops: List<PatchOp>,
        overrideDrift: Boolean = false,
    ): PatchResponse =
        c.call(
            "PATCH",
            listOf("api", "config"),
            json = ZeroClawJson.encodeToJsonElement(ListSerializer(PatchOp.serializer()), ops),
            headers = if (overrideDrift) mapOf(OVERRIDE_DRIFT_HEADER to "true") else emptyMap(),
        )

    /**
     * `GET /api/config/list`: every field reachable under `prefix` (all fields
     * without one), with its kind, category and current value.
     */
    suspend fun list(prefix: String? = null): ConfigList = c.call("GET", listOf("api", "config", "list"), query = mapOf("prefix" to prefix))

    /** `GET /api/config/drift`: the fields whose value on disk differs from the gateway's. */
    suspend fun drift(): List<DriftEntry> = c.call("GET", listOf("api", "config", "drift"), "drifted")

    /** `GET /api/config/reload-status`: whether saved changes wait for a daemon reload. */
    suspend fun pendingReload(): Boolean = c.call("GET", listOf("api", "config", "reload-status"), "pending_reload")

    /**
     * `POST /api/config/init`: fills unset nested sections under `section`
     * (everywhere without one) with defaults and returns those created.
     */
    suspend fun init(section: String? = null): List<String> =
        c.call("POST", listOf("api", "config", "init"), "initialized", query = mapOf("section" to section))

    /** `POST /api/config/migrate`: migrates `config.toml` to the current schema in place, keeping a backup. */
    suspend fun migrate(): MigrateResponse = c.call("POST", listOf("api", "config", "migrate"))

    /** `GET /api/config/templates`: the map and list sections and the type of their entries. */
    suspend fun templates(): List<TemplateEntry> = c.call("GET", listOf("api", "config", "templates"), "templates")

    /** `GET /api/config/map-keys`: the aliases of a map section, e.g. `agents` or `channels.whatsapp`. */
    suspend fun mapKeys(path: String): List<String> =
        c.call(
            "GET",
            listOf("api", "config", "map-keys"),
            "keys",
            query =
                mapOf(
                    "path" to path,
                ),
        )

    /**
     * `POST /api/config/map-key`: creates alias `key` under map `path` with
     * defaults; `created` is false when it already existed.
     */
    suspend fun createMapKey(
        path: String,
        key: String,
    ): MapKeyResponse = c.call("POST", listOf("api", "config", "map-key"), query = mapOf("path" to path, "key" to key))

    /**
     * `DELETE /api/config/map-key`: removes alias `key` and the references to
     * it. Deleting an agent also archives its workspace and its memory, cron
     * jobs and sessions; check [deletePlan] first.
     */
    suspend fun deleteMapKey(
        path: String,
        key: String,
    ): MapKeyResponse = c.call("DELETE", listOf("api", "config", "map-key"), query = mapOf("path" to path, "key" to key))

    /** `GET /api/config/delete-plan`: what deleting alias `key` would block on or rewrite, changing nothing. */
    suspend fun deletePlan(
        path: String,
        key: String,
    ): DeletePlan = c.call("GET", listOf("api", "config", "delete-plan"), query = mapOf("path" to path, "key" to key))

    /** `POST /api/config/rename-map-key`: renames alias `from` to `to`, and every reference to it. */
    suspend fun renameMapKey(
        path: String,
        from: String,
        to: String,
    ): RenameResponse =
        c.call(
            "POST",
            listOf("api", "config", "rename-map-key"),
            json =
                buildJsonObject {
                    put("path", path)
                    put("from", from)
                    put("to", to)
                },
        )

    /**
     * `POST /api/config/model-providers/{type}/{alias}/refresh-context-window`:
     * asks the provider for the model's context window and stores it.
     */
    suspend fun refreshContextWindow(
        providerType: String,
        alias: String,
    ): Long =
        c.call(
            "POST",
            listOf("api", "config", "model-providers", providerType, alias, "refresh-context-window"),
            "context_window",
        )

    /**
     * `GET /api/config/resolve-alias-source`: the aliases an alias-reference
     * field may point to; providers and channels as `type.alias`. `source` is
     * `model_providers`, `tts_providers`, `transcription_providers`,
     * `channels`, `risk_profiles`, `runtime_profiles`, `agents`,
     * `skill_bundles`, `knowledge_bundles` or `mcp_bundles`.
     */
    suspend fun aliasValues(source: String): List<String> =
        c.call("GET", listOf("api", "config", "resolve-alias-source"), "values", query = mapOf("source" to source))

    /** `GET /api/config/catalog`: the model providers this build knows. */
    suspend fun catalog(): List<CatalogModelProvider> = c.call("GET", listOf("api", "config", "catalog"), "model_providers")

    /**
     * `GET /api/config/catalog/models`: the models of a provider. It may call
     * the provider with the configured credentials.
     */
    suspend fun catalogModels(modelProvider: String): CatalogModels =
        c.call("GET", listOf("api", "config", "catalog", "models"), query = mapOf("model_provider" to modelProvider))

    /** `GET /api/config/status`: whether the setup is complete. */
    suspend fun status(): ConfigStatus = c.call("GET", listOf("api", "config", "status"))

    /** `GET /api/config/agent-options`: the aliases an agent can reference. */
    suspend fun agentOptions(): AgentOptions = c.call("GET", listOf("api", "config", "agent-options"))

    /** `GET /api/config/sections`: the setup sections, in wizard order. */
    suspend fun sections(): List<ConfigSection> = c.call("GET", listOf("api", "config", "sections"), "sections")

    /** `GET /api/config/sections/{section}`: the items a picker section offers, e.g. the provider families of `providers.models`. */
    suspend fun sectionItems(section: String): SectionItems = c.call("GET", listOf("api", "config", "sections", section))

    /**
     * `POST /api/config/sections/{section}/items/{key}`: picks an item,
     * creating what it needs, and returns where its fields live. Without
     * `alias` the gateway uses `default` where the section takes one.
     */
    suspend fun selectSectionItem(
        section: String,
        key: String,
        alias: String? = null,
    ): SelectedItem =
        c.call(
            "POST",
            listOf("api", "config", "sections", section, "items", key),
            json = buildJsonObject { put("alias", alias?.ifEmpty { null }) },
        )
}
