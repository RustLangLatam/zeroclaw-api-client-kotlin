package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.fail

/**
 * Response vectors: answers captured from a real gateway (the Rust client's
 * `scripts/capture-vectors.py`, anonymized). Each must decode into the
 * client's type, and every field the type keeps must come out unchanged.
 */
class VectorsTest {
    private val agent = "zc_agent_00000000000000000000000000000000"

    private val cases: List<Triple<String, String?, suspend (Client) -> Any?>> =
        listOf(
            Triple("agent_skills", null) { c -> c.agent(agent).skills() },
            Triple("channels", "channels") { c -> c.channels.list() },
            Triple("config_agent_options", null) { c -> c.config.agentOptions() },
            Triple("config_catalog", "model_providers") { c -> c.config.catalog() },
            Triple("config_drift", "drifted") { c -> c.config.drift() },
            Triple("config_list", null) { c -> c.config.list("gateway") },
            Triple("config_map_keys", "keys") { c -> c.config.mapKeys("agents") },
            Triple("config_reload_status", "pending_reload") { c -> c.config.pendingReload() },
            Triple("config_sections", "sections") { c -> c.config.sections() },
            Triple("config_status", null) { c -> c.config.status() },
            Triple("config_templates", "templates") { c -> c.config.templates() },
            Triple("cost", "cost") { c -> c.cost.summary() },
            Triple("cron_list", "jobs") { c -> c.cron.list() },
            Triple("cron_settings", null) { c -> c.cron.settings() },
            Triple("devices", "devices") { c -> c.pairing.devices() },
            Triple("events_history", "events") { c -> c.events.history() },
            Triple("health", null) { c -> c.system.health() },
            Triple("integration_settings", "settings") { c -> c.system.integrationSettings() },
            Triple("integrations", "integrations") { c -> c.system.integrations() },
            Triple("memory_list", "entries") { c -> c.memory.search() },
            Triple("personality_files", null) { c -> c.agent(agent).personalityFiles() },
            Triple("quickstart_state", null) { c -> c.quickstart.state() },
            Triple("runtime_health", "health") { c -> c.system.runtimeHealth() },
            Triple("sessions", "sessions") { c -> c.sessions.list() },
            Triple("skill_bundles", "bundles") { c -> c.skills.bundles() },
            Triple("slash_option_kinds", "kinds") { c -> c.skills.slashOptionKinds() },
            Triple("status", null) { c -> c.system.status() },
            Triple("tools", "tools") { c -> c.system.tools() },
            Triple("tuis", "tuis") { c -> c.system.tuis() },
        )

    @Test
    fun `every captured answer decodes and keeps its values`() {
        val failures = mutableListOf<String>()
        for ((name, field, call) in cases) {
            val vector = obj(javaClass.getResource("/vectors/$name.json")!!.readText()) as JsonObject
            val route = (vector["route"] as JsonPrimitive).content.substringBefore("?")
            val response = vector["response"] as JsonObject
            Gateway().use { gw ->
                gw.json(response.toString())
                try {
                    val result = runBlocking { call(gw.client) }
                    val path =
                        gw.server
                            .takeRequest()
                            .requestUrl!!
                            .encodedPath
                    if (path != route) failures += "$name: path $path"
                    val expected = if (field == null) response else response[field]!!
                    if (!contains(expected, toJson(result))) failures += "$name: ${toJson(result)}"
                } catch (e: ZeroClawException) {
                    failures += "$name: $e"
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }
}
