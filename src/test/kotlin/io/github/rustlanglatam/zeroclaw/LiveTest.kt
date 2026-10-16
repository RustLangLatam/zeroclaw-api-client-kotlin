package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.fail

/**
 * Read-only calls against a real gateway, run only with `ZEROCLAW_URL` and
 * `ZEROCLAW_TOKEN` set: every answer must decode. A route the build leaves
 * out (404) or a disabled subsystem (503) is reported, not failed.
 */
class LiveTest {
    @Test
    fun `read-only calls decode against a live gateway`() {
        val url = System.getenv("ZEROCLAW_URL").orEmpty()
        assumeTrue(url.isNotEmpty(), "ZEROCLAW_URL is not set")
        val c = Client(url, ClientOptions(token = System.getenv("ZEROCLAW_TOKEN")))
        val failures = mutableListOf<String>()
        var ok = 0

        suspend fun check(
            name: String,
            call: suspend () -> Any?,
        ) {
            try {
                call()
                ok++
                println("ok      $name")
            } catch (e: ApiException) {
                if (e.status == 404 || e.status == 503) println("skipped $name: $e") else failures += "$name: $e"
            } catch (e: ZeroClawException) {
                failures += "$name: $e"
            }
        }
        runBlocking {
            check("health") { c.system.health() }
            check("runtimeHealth") { c.system.runtimeHealth() }
            check("status") { c.system.status() }
            check("metrics") { c.system.metrics() }
            check("tools") { c.system.tools() }
            check("integrations") { c.system.integrations() }
            check("integrationSettings") { c.system.integrationSettings() }
            check("tuis") { c.system.tuis() }
            check("logs") { c.system.logs(LogQuery(limit = 5)) }
            check("channels") { c.channels.list() }
            for (channel in runCatching { c.channels.list() }.getOrDefault(emptyList()).take(1)) {
                check("channels.rooms") { c.channels.rooms(channel.name) }
            }
            check("config.status") { c.config.status() }
            check("config.sections") { c.config.sections() }
            check("config.list") { c.config.list("gateway") }
            check("config.catalog") { c.config.catalog() }
            check("config.templates") { c.config.templates() }
            check("config.agentOptions") { c.config.agentOptions() }
            check("config.drift") { c.config.drift() }
            check("config.pendingReload") { c.config.pendingReload() }
            check("config.mapKeys") { c.config.mapKeys("agents") }
            check("cost") { c.cost.summary() }
            check("cron.list") { c.cron.list() }
            check("cron.settings") { c.cron.settings() }
            check("devices") { c.pairing.devices() }
            check("events.history") { c.events.history() }
            check("events.subscribe") { c.events.subscribe().close() }
            check("sessions") { c.sessions.list() }
            check("memory") { c.memory.search() }
            check("canvas") { c.canvas.list() }
            check("canvas.watch") { c.canvas.watch("live-check").use { it.next() } }
            check("skills.bundles") { c.skills.bundles() }
            check("skills.slashOptionKinds") { c.skills.slashOptionKinds() }
            check("sops.runs") { c.sops.runs() }
            check("quickstart.state") { c.quickstart.state() }
            for (agent in runCatching { c.config.mapKeys("agents") }.getOrDefault(emptyList()).take(1)) {
                check("agent.personalityFiles") { c.agent(agent).personalityFiles() }
                check("agent.skills") { c.agent(agent).skills() }
                check("agent.listFiles") { c.agent(agent).listFiles() }
            }
        }
        println("$ok live reads decoded")
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }
}
