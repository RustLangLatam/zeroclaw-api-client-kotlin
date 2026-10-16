package io.github.rustlanglatam.zeroclaw

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Every endpoint at the HTTP boundary: method, path, query, body, and what the call returns from the gateway's answer. */
class EndpointsTest {
    class Case(
        val method: String,
        val path: String,
        val query: Map<String, String> = emptyMap(),
        /** The JSON body the gateway expects; `null` for none. */
        val body: String? = null,
        val headers: Map<String, String> = emptyMap(),
        /** The gateway's answer (JSON); `{}` when unset. */
        val reply: String? = null,
        /** What the call returns; checked when set. */
        val result: String? = null,
        val call: suspend (Client) -> Any?,
    )

    private val cases =
        listOf(
            Case(
                "GET",
                "/api/agents/helper/workspace/list",
                query = mapOf("path" to "notes"),
                reply = """{"path":"notes","entries":[]}""",
                result = """{"path":"notes","entries":[]}""",
            ) { c -> c.agent("helper").listFiles("notes") },
            Case(
                "GET",
                "/api/agents/helper/workspace/read",
                query = mapOf("path" to "a b.txt"),
            ) { c -> c.agent("helper").readFile("a b.txt") },
            Case("POST", "/api/agents/helper/workspace/mkdir", body = """{"path":"d"}""") { c -> c.agent("helper").makeDir("d") },
            Case(
                "POST",
                "/api/agents/helper/workspace/move",
                body = """{"from":"a","to":"b"}""",
            ) { c -> c.agent("helper").movePath("a", "b") },
            Case("DELETE", "/api/agents/helper/workspace/path", body = """{"path":"a"}""") { c -> c.agent("helper").removePath("a") },
            Case("GET", "/api/personality", query = mapOf("agent" to "helper")) { c -> c.agent("helper").personalityFiles() },
            Case("GET", "/api/personality/SOUL.md", query = mapOf("agent" to "helper")) { c -> c.agent("helper").personality("SOUL.md") },
            Case(
                "PUT",
                "/api/personality/SOUL.md",
                query = mapOf("agent" to "helper"),
                body = """{"content":"be kind","expected_mtime_ms":42}""",
            ) { c -> c.agent("helper").writePersonality("SOUL.md", "be kind", 42) },
            Case(
                "PUT",
                "/api/personality/SOUL.md",
                query = mapOf("agent" to "helper"),
                body = """{"content":"x","expected_mtime_ms":null}""",
            ) { c -> c.agent("helper").writePersonality("SOUL.md", "x") },
            Case(
                "GET",
                "/api/personality/templates",
                query = mapOf("agent" to "helper", "user_name" to "Ana", "include_memory" to "true"),
            ) { c -> c.agent("helper").personalityTemplates(PersonalityContext(userName = "Ana", includeMemory = true)) },
            Case("GET", "/api/agents/helper/skills") { c -> c.agent("helper").skills() },
            Case("GET", "/api/canvas", reply = """{"canvases":["a"]}""", result = """["a"]""") { c -> c.canvas.list() },
            Case(
                "GET",
                "/api/canvas/a",
                reply = """{"frame":{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}}""",
                result = """{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}""",
            ) { c -> c.canvas.current("a") },
            Case(
                "POST",
                "/api/canvas/a",
                body = """{"content_type":"text","content":"hi"}""",
                reply = """{"frame":{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}}""",
                result = """{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}""",
            ) { c -> c.canvas.publish("a", "text", "hi") },
            Case(
                "GET",
                "/api/canvas/a/history",
                reply = """{"frames":[{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}]}""",
                result = """[{"frame_id":"f","content_type":"text","content":"hi","timestamp":"t"}]""",
            ) { c -> c.canvas.history("a") },
            Case("DELETE", "/api/canvas/a") { c -> c.canvas.clear("a") },
            Case("GET", "/api/channels", reply = """{"channels":[]}""", result = """[]""") { c -> c.channels.list() },
            Case(
                "POST",
                "/api/channels/bind",
                body = """{"channel_type":"telegram","alias":"bot","identity":"42"}""",
            ) { c -> c.channels.bind("telegram", "bot", "42") },
            Case("POST", "/api/channels/whatsapp.main/relink") { c -> c.channels.relink("whatsapp.main") },
            Case("GET", "/api/config") { c -> c.config.snapshot() },
            Case("OPTIONS", "/api/config") { c -> c.config.schema() },
            Case("OPTIONS", "/api/config/prop", query = mapOf("path" to "a.b")) { c -> c.config.propSchema("a.b") },
            Case("GET", "/api/config/prop", query = mapOf("path" to "a.b")) { c -> c.config.get("a.b") },
            Case("PUT", "/api/config/prop", body = """{"path":"a.b","value":3,"comment":"why"}""") { c -> c.config.set("a.b", 3, "why") },
            Case("PUT", "/api/config/prop", body = """{"path":"a.b","value":null}""") { c -> c.config.set("a.b", JsonNull) },
            Case("DELETE", "/api/config/prop", query = mapOf("path" to "a.b")) { c -> c.config.reset("a.b") },
            Case(
                "PATCH",
                "/api/config",
                body = """[{"op":"replace","path":"a","value":1},{"op":"remove","path":"b"}]""",
                headers =
                    mapOf(
                        "x-zeroclaw-override-drift" to "true",
                    ),
            ) { c -> c.config.patch(listOf(PatchOp.replace("a", JsonPrimitive(1)), PatchOp.remove("b")), true) },
            Case("GET", "/api/config/list", query = mapOf("prefix" to "gateway")) { c -> c.config.list("gateway") },
            Case("GET", "/api/config/drift", reply = """{"drifted":[]}""", result = """[]""") { c -> c.config.drift() },
            Case(
                "GET",
                "/api/config/reload-status",
                reply = """{"pending_reload":true}""",
                result = """true""",
            ) { c -> c.config.pendingReload() },
            Case(
                "POST",
                "/api/config/init",
                query = mapOf("section" to "agents"),
                reply = """{"initialized":["agents.x"]}""",
                result = """["agents.x"]""",
            ) { c -> c.config.init("agents") },
            Case("POST", "/api/config/migrate") { c -> c.config.migrate() },
            Case("GET", "/api/config/templates", reply = """{"templates":[]}""", result = """[]""") { c -> c.config.templates() },
            Case(
                "GET",
                "/api/config/map-keys",
                query = mapOf("path" to "agents"),
                reply = """{"keys":["helper"]}""",
                result = """["helper"]""",
            ) { c -> c.config.mapKeys("agents") },
            Case(
                "POST",
                "/api/config/map-key",
                query = mapOf("path" to "agents", "key" to "helper"),
            ) { c -> c.config.createMapKey("agents", "helper") },
            Case(
                "DELETE",
                "/api/config/map-key",
                query = mapOf("path" to "agents", "key" to "helper"),
            ) { c -> c.config.deleteMapKey("agents", "helper") },
            Case(
                "GET",
                "/api/config/delete-plan",
                query = mapOf("path" to "agents", "key" to "helper"),
            ) { c -> c.config.deletePlan("agents", "helper") },
            Case(
                "POST",
                "/api/config/rename-map-key",
                body = """{"path":"agents","from":"a","to":"b"}""",
            ) { c -> c.config.renameMapKey("agents", "a", "b") },
            Case(
                "POST",
                "/api/config/model-providers/openai/default/refresh-context-window",
                reply = """{"context_window":128000}""",
                result = """128000""",
            ) { c -> c.config.refreshContextWindow("openai", "default") },
            Case(
                "GET",
                "/api/config/resolve-alias-source",
                query = mapOf("source" to "agents"),
                reply = """{"values":["helper"]}""",
                result = """["helper"]""",
            ) { c -> c.config.aliasValues("agents") },
            Case("GET", "/api/config/catalog", reply = """{"model_providers":[]}""", result = """[]""") { c -> c.config.catalog() },
            Case(
                "GET",
                "/api/config/catalog/models",
                query = mapOf("model_provider" to "openai"),
            ) { c -> c.config.catalogModels("openai") },
            Case("GET", "/api/config/status") { c -> c.config.status() },
            Case("GET", "/api/config/agent-options") { c -> c.config.agentOptions() },
            Case("GET", "/api/config/sections", reply = """{"sections":[]}""", result = """[]""") { c -> c.config.sections() },
            Case("GET", "/api/config/sections/providers.models") { c -> c.config.sectionItems("providers.models") },
            Case(
                "POST",
                "/api/config/sections/providers.models/items/openai",
                body = """{"alias":null}""",
            ) { c -> c.config.selectSectionItem("providers.models", "openai") },
            Case(
                "GET",
                "/api/cost",
                query = mapOf("from" to "2026-01-01T00:00:00Z"),
                reply = """{"cost":{"total_tokens":3}}""",
                result = """{"total_tokens":3}""",
            ) { c -> c.cost.summary("2026-01-01T00:00:00Z") },
            Case("GET", "/api/cost", query = mapOf("agent" to "helper"), reply = """{"cost":{}}""") { c -> c.cost.agent("helper") },
            Case(
                "GET",
                "/api/cron",
                reply = """{"jobs":[{"id":"j1","job_type":"shell"}]}""",
                result = """[{"id":"j1","job_type":"shell"}]""",
            ) { c -> c.cron.list() },
            Case(
                "POST",
                "/api/cron",
                body =
                    """
                    {"agent":"helper","schedule":"0 9 * * *","command":"date",
                    "delivery":{"mode":"announce","channel":"whatsapp.main","to":"+34600000000","best_effort":true}}
                    """.trimIndent(),
                reply = """{"job":{"id":"j1","job_type":"shell"}}""",
                result = """{"id":"j1","job_type":"shell"}""",
            ) { c ->
                c.cron.create(
                    NewCronJob(
                        agent = "helper",
                        schedule = "0 9 * * *",
                        command = "date",
                        delivery = announce("whatsapp.main", "+34600000000"),
                    ),
                )
            },
            Case(
                "PATCH",
                "/api/cron/j1",
                body = """{"enabled":false}""",
                reply = """{"job":{"id":"j1","job_type":"shell"}}""",
                result = """{"id":"j1","job_type":"shell"}""",
            ) { c -> c.cron.update("j1", CronJobPatch(enabled = false)) },
            Case("DELETE", "/api/cron/j1") { c -> c.cron.delete("j1") },
            Case("POST", "/api/cron/j1/run") { c -> c.cron.run("j1") },
            Case(
                "GET",
                "/api/cron/j1/runs",
                query = mapOf("limit" to "5"),
                reply = """{"runs":[]}""",
                result = """[]""",
            ) { c -> c.cron.runs("j1", 5) },
            Case("GET", "/api/cron/settings") { c -> c.cron.settings() },
            Case(
                "PATCH",
                "/api/cron/settings",
                body = """{"enabled":false}""",
            ) { c -> c.cron.updateSettings(CronSettingsPatch(enabled = false)) },
            Case(
                "GET",
                "/api/events/history",
                reply = """{"events":[{"type":"agent_end"}]}""",
                result = """[{"type":"agent_end"}]""",
            ) { c -> c.events.history() },
            Case("GET", "/api/browse") { c -> c.files.list() },
            Case("POST", "/api/browse/mkdir", body = """{"path":"d"}""") { c -> c.files.makeDir("d") },
            Case("DELETE", "/api/browse/rmdir", body = """{"path":"d"}""") { c -> c.files.removeDir("d") },
            Case(
                "GET",
                "/api/memory",
                query = mapOf("query" to "orders", "agent" to "helper"),
                reply = """{"entries":[]}""",
                result = """[]""",
            ) { c -> c.memory.search(MemorySearch(query = "orders", agent = "helper")) },
            Case(
                "POST",
                "/api/memory",
                body = """{"key":"k","content":"v","category":null,"agent":null}""",
            ) { c -> c.memory.store("k", "v") },
            Case(
                "DELETE",
                "/api/memory/a%2Fb",
                query = mapOf("agent" to "helper"),
                reply = """{"deleted":true}""",
                result = """true""",
            ) { c -> c.memory.forget("a/b", "helper") },
            Case(
                "POST",
                "/api/pair",
                body = """{"code":"123456","device_name":"laptop","device_type":null}""",
            ) { c -> c.pairing.pair("123456", "laptop") },
            Case("POST", "/pair", headers = mapOf("x-pairing-code" to "123456")) { c -> c.pairing.pairLegacy("123456") },
            Case("GET", "/pair/code") { c -> c.pairing.initialCode() },
            Case(
                "POST",
                "/api/pairing/initiate",
                reply = """{"pairing_code":"654321"}""",
                result = """"654321"""",
            ) { c -> c.pairing.newCode() },
            Case("GET", "/admin/paircode") { c -> c.pairing.adminCode() },
            Case("POST", "/admin/paircode/new", query = mapOf("rotate" to "all")) { c -> c.pairing.adminNewCode("all") },
            Case("GET", "/api/devices", reply = """{"devices":[]}""", result = """[]""") { c -> c.pairing.devices() },
            Case("DELETE", "/api/devices/d1") { c -> c.pairing.revokeDevice("d1") },
            Case(
                "POST",
                "/api/devices/d1/token/rotate",
                reply = """{"pairing_code":null}""",
                result = """null""",
            ) { c -> c.pairing.rotateDeviceToken("d1") },
            Case(
                "POST",
                "/api/devices/me/capabilities",
                body = """{"capabilities":["chat"]}""",
                reply = """{"capabilities":["chat"]}""",
                result = """["chat"]""",
            ) { c -> c.pairing.setCapabilities(listOf("chat")) },
            Case("GET", "/api/quickstart/state") { c -> c.quickstart.state() },
            Case(
                "POST",
                "/api/quickstart/fields",
                body = """{"section":"channel","type_key":"whatsapp"}""",
            ) { c -> c.quickstart.fields("channel", "whatsapp") },
            Case("POST", "/api/quickstart/validate", body = """{"a":1}""") { c -> c.quickstart.validate(obj("""{"a":1}""")) },
            Case("POST", "/api/quickstart/apply", body = """{"a":1}""") { c -> c.quickstart.apply(obj("""{"a":1}""")) },
            Case(
                "POST",
                "/api/quickstart/dismiss",
                body = """{"run_id":"r1","surface":"web","last_step":null}""",
            ) { c -> c.quickstart.dismiss("r1", "web") },
            Case("GET", "/api/sessions", reply = """{"sessions":[]}""", result = """[]""") { c -> c.sessions.list() },
            Case("GET", "/api/sessions/running", reply = """{"sessions":[]}""", result = """[]""") { c -> c.sessions.running() },
            Case(
                "GET",
                "/api/sessions/gw_1/messages",
                reply = """{"messages":[]}""",
                result = """[]""",
            ) { c -> c.sessions.messages("gw_1") },
            Case("POST", "/api/sessions/gw_1/messages", body = """{"content":"hi"}""") { c -> c.sessions.addMessage("gw_1", "hi") },
            Case("GET", "/api/sessions/gw_1/state") { c -> c.sessions.state("gw_1") },
            Case("PUT", "/api/sessions/gw_1", body = """{"name":"n"}""") { c -> c.sessions.rename("gw_1", "n") },
            Case("DELETE", "/api/sessions/gw_1") { c -> c.sessions.delete("gw_1") },
            Case(
                "POST",
                "/api/sessions/gw_1/abort",
                reply = """{"status":"aborted"}""",
                result = """true""",
            ) { c -> c.sessions.abort("gw_1") },
            Case("GET", "/api/skills/bundles", reply = """{"bundles":[]}""", result = """[]""") { c -> c.skills.bundles() },
            Case("GET", "/api/skills/bundles/main/skills", reply = """{"skills":[]}""", result = """[]""") { c -> c.skills.list("main") },
            Case(
                "POST",
                "/api/skills/bundles/main/skills",
                body = """{"name":"s","frontmatter":{"description":"d"},"body":"body","no_scaffold":false}""",
            ) { c -> c.skills.create("main", "s", obj("""{"description":"d"}"""), "body") },
            Case("GET", "/api/skills/bundles/main/skills/s") { c -> c.skills.read("main", "s") },
            Case(
                "PUT",
                "/api/skills/bundles/main/skills/s",
                body = """{"frontmatter":{},"body":"b"}""",
            ) { c -> c.skills.write("main", "s", obj("{}"), "b") },
            Case("DELETE", "/api/skills/bundles/main/skills/s", query = mapOf("purge" to "false")) { c -> c.skills.delete("main", "s") },
            Case(
                "GET",
                "/api/skills/slash-option-kinds",
                reply = """{"kinds":[]}""",
                result = """[]""",
            ) { c -> c.skills.slashOptionKinds() },
            Case("GET", "/api/sops", reply = """{"sops":[]}""", result = """[]""") { c -> c.sops.list() },
            Case("GET", "/api/sops/s/full") { c -> c.sops.get("s") },
            Case("POST", "/api/sops", body = """{"name":"s"}""") { c -> c.sops.create(obj("""{"name":"s"}""")) },
            Case("PUT", "/api/sops/s", body = """{"name":"s"}""") { c -> c.sops.save("s", obj("""{"name":"s"}""")) },
            Case("DELETE", "/api/sops/s") { c -> c.sops.delete("s") },
            Case("GET", "/api/sops/s/graph") { c -> c.sops.graph("s") },
            Case("POST", "/api/sops/graph-draft", body = """{"sop":{"name":"s"}}""") { c -> c.sops.graphDraft(obj("""{"name":"s"}""")) },
            Case(
                "POST",
                "/api/sops/wire-draft",
                body = """{"sop":{"name":"s"},"edit":{"op":"connect"}}""",
            ) { c -> c.sops.wireDraft(obj("""{"name":"s"}"""), obj("""{"op":"connect"}""")) },
            Case("GET", "/api/sops/graph-legend") { c -> c.sops.graphLegend() },
            Case("GET", "/api/sops/trigger-sources") { c -> c.sops.triggerSources() },
            Case(
                "POST",
                "/api/sops/s/run",
                body = """{"payload":"{\"x\":1}"}""",
                reply = """{"run_id":"r1"}""",
                result = """"r1"""",
            ) { c -> c.sops.run("s", """{"x":1}""") },
            Case(
                "GET",
                "/api/sops/runs",
                query = mapOf("sop" to "s"),
                reply = """{"runs":[]}""",
                result = """[]""",
            ) { c -> c.sops.runs("s") },
            Case("GET", "/api/sops/s/runs/r1/overlay") { c -> c.sops.runOverlay("s", "r1") },
            Case("POST", "/api/sops/s/runs/r1/decide", body = """"approve"""") { c -> c.sops.decide("s", "r1", SopDecision.approve()) },
            Case(
                "POST",
                "/api/sops/s/runs/r1/decide",
                body = """{"deny":{"reason":"no"}}""",
            ) { c -> c.sops.decide("s", "r1", SopDecision.deny("no")) },
            Case(
                "POST",
                "/api/sops/s/runs/r1/decide",
                body = """{"revise":{"guidance":"again"}}""",
            ) { c -> c.sops.decide("s", "r1", SopDecision.revise("again")) },
            Case("POST", "/api/sops/s/runs/r1/cancel", body = """{"reason":null}""") { c -> c.sops.cancel("s", "r1") },
            Case("GET", "/admin/sop/pending", reply = """{"pending":[]}""", result = """[]""") { c -> c.sops.pending() },
            Case(
                "POST",
                "/admin/sop/approve",
                body = """{"run_id":"r1","reason":null}""",
                reply = """{"outcome":"resumed"}""",
                result = """"resumed"""",
            ) { c -> c.sops.approve("r1") },
            Case(
                "POST",
                "/admin/sop/deny",
                body = """{"run_id":"r1","reason":"no"}""",
                reply = """{"outcome":"denied"}""",
                result = """"denied"""",
            ) { c -> c.sops.deny("r1", "no") },
            Case("GET", "/health") { c -> c.system.health() },
            Case("GET", "/api/health", reply = """{"health":{"pid":1}}""", result = """{"pid":1}""") { c -> c.system.runtimeHealth() },
            Case("GET", "/api/status", query = mapOf("agent" to "helper")) { c -> c.system.status("helper") },
            Case("POST", "/api/doctor") { c -> c.system.doctor() },
            Case("GET", "/api/tools", reply = """{"tools":[]}""", result = """[]""") { c -> c.system.tools() },
            Case(
                "POST",
                "/api/tools/param-options",
                body = """{"domain":"agent_aliases","agent":null,"args":null}""",
                reply = """{"options":[]}""",
                result = """[]""",
            ) { c -> c.system.toolParamOptions("agent_aliases") },
            Case("GET", "/api/integrations", reply = """{"integrations":[]}""", result = """[]""") { c -> c.system.integrations() },
            Case(
                "GET",
                "/api/integrations/settings",
                reply = """{"settings":{}}""",
                result = """{}""",
            ) { c -> c.system.integrationSettings() },
            Case("GET", "/api/cli-tools", reply = """{"cli_tools":[]}""", result = """[]""") { c -> c.system.cliTools() },
            Case("GET", "/api/tuis", reply = """{"tuis":[]}""", result = """[]""") { c -> c.system.tuis() },
            Case("GET", "/api/logs", query = mapOf("q" to "boom", "limit" to "10", "hide_internal" to "true", "channel" to "x")) { c ->
                c.system.logs(
                    LogQuery(
                        q = "boom",
                        limit = 10,
                        hideInternal = true,
                        attributes =
                            mapOf(
                                "channel" to "x",
                            ),
                    ),
                )
            },
            Case("GET", "/api/version/check", query = mapOf("force" to "true")) { c -> c.system.versionCheck(true) },
            Case(
                "POST",
                "/api/version/upgrade",
                body = """{"version":null,"auto_restart":false}""",
                reply = """{"handoff_id":"h1"}""",
                result = """"h1"""",
            ) { c -> c.system.upgrade() },
            Case("GET", "/api/version/upgrade/status", query = mapOf("handoff_id" to "h1")) { c -> c.system.upgradeStatus("h1") },
            Case("POST", "/admin/reload") { c -> c.system.reload() },
            Case("POST", "/admin/shutdown") { c -> c.system.shutdown() },
            Case("GET", "/api/openapi.json") { c -> c.system.openapi() },
            Case(
                "POST",
                "/api/webauthn/register/start",
                body = """{"user_id":"u","user_name":"Ana"}""",
            ) { c -> c.webauthn.registerStart("u", "Ana") },
            Case(
                "POST",
                "/api/webauthn/register/finish",
                body = """{"id":"k"}""",
            ) { c -> c.webauthn.registerFinish(obj("""{"id":"k"}""")) },
            Case("POST", "/api/webauthn/auth/start", body = """{"user_id":"u"}""") { c -> c.webauthn.authStart("u") },
            Case("POST", "/api/webauthn/auth/finish", body = """{"id":"k"}""") { c -> c.webauthn.authFinish(obj("""{"id":"k"}""")) },
            Case("GET", "/api/webauthn/credentials", query = mapOf("user_id" to "u")) { c -> c.webauthn.credentials("u") },
            Case("DELETE", "/api/webauthn/credentials/k", query = mapOf("user_id" to "u")) { c -> c.webauthn.deleteCredential("u", "k") },
            Case(
                "POST",
                "/webhook",
                query = mapOf("agent" to "helper"),
                body = """{"message":"hi"}""",
                headers =
                    mapOf(
                        "x-idempotency-key" to "k1",
                    ),
                reply = """{"response":"hello","model":"m"}""",
                result = """{"duplicate":false,"reply":{"response":"hello","model":"m"}}""",
            ) { c -> c.webhook.send("hi", WebhookOptions(agent = "helper", idempotencyKey = "k1")) },
            Case(
                "POST",
                "/webhook",
                body = """{"message":"hi"}""",
                reply = """{"status":"duplicate"}""",
                result = """{"duplicate":true}""",
            ) { c -> c.webhook.send("hi") },
            Case(
                "POST",
                "/a2a/helper",
                body = """{"jsonrpc":"2.0","id":1,"method":"message/send","params":{"message":{"parts":[{"kind":"text","text":"hi"}]}}}""",
                reply = """{"result":{"artifacts":[{"parts":[{"text":"a"},{"kind":"data"},{"text":"b"}]}]}}""",
                result = """"a\nb"""",
            ) { c -> c.webhook.a2aSend("helper", "hi") },
            Case("GET", "/a2a/helper/.well-known/agent-card.json") { c -> c.webhook.agentCard("helper") },
            Case("GET", "/.well-known/agents-card.json") { c -> c.webhook.agentCatalog() },
            Case("POST", "/api/channels/whatsapp.main/send", body = """{"to":"+34600000000","content":"Hola","thread_id":null}""") { c ->
                c.channels.send("whatsapp.main", "+34600000000", "Hola")
            },
            Case(
                "POST",
                "/api/channels/email.box/messages/a@b.example/redeliver",
                reply = """{"channel":"email.box","message_id":"a@b.example","outcome":"redelivered"}""",
                result = """{"channel":"email.box","message_id":"a@b.example","outcome":"redelivered"}""",
            ) { c -> c.channels.redeliver("email.box", "a@b.example") },
            Case(
                "GET",
                "/api/channels/whatsapp.main/rooms",
                reply = """{"channel":"whatsapp.main","rooms":[{"id":"1203@g.us","name":"Team","participants":3}]}""",
                result = """[{"id":"1203@g.us","name":"Team","participants":3}]""",
            ) { c -> c.channels.rooms("whatsapp.main") },
            Case("POST", "/api/channels/whatsapp.main/rooms", body = """{"name":"Team","topic":null,"invites":["+34600000000"]}""") { c ->
                c.channels.createRoom("whatsapp.main", "Team", null, listOf("+34600000000"))
            },
            Case(
                "GET",
                "/api/channels/whatsapp.main/rooms/1203@g.us/members",
                reply = """{"members":[{"id":"+34600000000","admin":true}]}""",
                result = """[{"id":"+34600000000","admin":true}]""",
            ) { c -> c.channels.roomMembers("whatsapp.main", "1203@g.us") },
            Case("POST", "/api/channels/whatsapp.main/rooms/1203@g.us/invites", body = """{"user":"+34611111111"}""") { c ->
                c.channels.invite("whatsapp.main", "1203@g.us", "+34611111111")
            },
            Case("DELETE", "/api/channels/whatsapp.main/rooms/1203@g.us/members/+34611111111") { c ->
                c.channels.removeMember("whatsapp.main", "1203@g.us", "+34611111111")
            },
        )

    @Test
    fun `every endpoint sends what the gateway expects`() {
        val failures = mutableListOf<String>()
        for (case in cases) {
            Gateway().use { gw ->
                gw.json(case.reply ?: "{}")
                var decoded = true
                val returned =
                    try {
                        runBlocking { case.call(gw.client) }
                    } catch (e: DecodeException) {
                        // A typed answer cannot come out of a placeholder reply that
                        // leaves out required fields; the vectors test decodes real ones.
                        if (case.reply != null && e.cause !is kotlinx.serialization.MissingFieldException) {
                            failures += "${case.method} ${case.path}: $e"
                        }
                        decoded = false
                        null
                    }
                val seen = gw.server.takeRequest()
                val name = "${case.method} ${case.path}"
                if (seen.method != case.method) failures += "$name: method ${seen.method}"
                if (seen.requestUrl!!.encodedPath != case.path) failures += "$name: path ${seen.requestUrl!!.encodedPath}"
                val query = seen.requestUrl!!.queryParameterNames.associateWith { seen.requestUrl!!.queryParameter(it) }
                if (query != case.query) failures += "$name: query $query"
                val text = seen.body.readUtf8()
                if (case.body != null && (text.isEmpty() || obj(text) != obj(case.body))) failures += "$name: body $text"
                if (case.body == null && text.isNotEmpty()) failures += "$name: unexpected body $text"
                for ((k, v) in case.headers) if (seen.getHeader(k) != v) failures += "$name: header $k=${seen.getHeader(k)}"
                if (seen.getHeader("Authorization") != "Bearer $TOKEN") failures += "$name: no token"
                if (decoded && case.result != null && !contains(toJson(returned), obj(case.result))) {
                    failures += "$name: result ${toJson(returned)}"
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
        assertEquals(149, cases.size)
    }

    @Test
    fun `POST upload sends the image bytes`() =
        Gateway().use { gw ->
            gw.json("""{"path":"p","marker":"[IMAGE:p]"}""")
            val upload = runBlocking { gw.client.files.uploadImage("helper", byteArrayOf(1, 2, 3)) }
            val seen = gw.server.takeRequest()
            assertEquals("[IMAGE:p]", upload.marker)
            assertEquals("application/octet-stream", seen.getHeader("Content-Type"))
            assertTrue(seen.body.readByteArray().contentEquals(byteArrayOf(1, 2, 3)))
        }

    @Test
    fun `GET metrics returns the exposition text`() =
        Gateway().use { gw ->
            gw.json("# HELP x\nx 1\n")
            assertEquals("# HELP x\nx 1\n", runBlocking { gw.client.system.metrics() })
        }

    @Test
    fun `an A2A JSON-RPC error is an ApiException with status 200`() =
        Gateway().use { gw ->
            gw.json("""{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"no such method"}}""")
            val e = runCatching { runBlocking { gw.client.webhook.a2aSend("helper", "hi") } }.exceptionOrNull()
            assertTrue(e is ApiException && e.status == 200 && e.reason == "no such method", "$e")
        }

    @Test
    fun `a call that misses its envelope field is a DecodeException`() =
        Gateway().use { gw ->
            gw.json("""{"other":[]}""")
            val e = runCatching { runBlocking { gw.client.cron.list() } }.exceptionOrNull()
            assertTrue(e is DecodeException, "$e")
        }

    @Test
    fun `a set field writes typed JSON`() =
        Gateway().use { gw ->
            gw.json("""{"path":"a","value":"1"}""")
            runBlocking { gw.client.config.set("a", 1) }
            assertEquals(
                obj("""{"path":"a","value":1}"""),
                obj(
                    gw.server
                        .takeRequest()
                        .body
                        .readUtf8(),
                ),
            )
            assertEquals(JsonPrimitive(1), obj("1"))
        }

    @Test
    fun `a message the gateway no longer keeps is not found`() =
        Gateway().use { gw ->
            gw.json("""{"channel":"email.box","error":"message not kept"}""", status = 404)
            val e = runCatching { runBlocking { gw.client.channels.redeliver("email.box", "gone") } }.exceptionOrNull()
            assertTrue(e != null && e.isNotFound, "$e")
        }
}
