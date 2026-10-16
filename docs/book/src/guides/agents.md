# Agents, personality and skills

An agent is a configuration entry (`agents.<alias>`): create, rename and
delete agents with the [configuration](./configuration.md) calls. Its files
and skills are reached through `client.agent(alias)`.

## Workspace

Each agent has a workspace directory. Paths are relative to it; leaving it is
refused, and the personality files and `sessions` at its root are protected.

```kotlin
val agent = client.agent("helper")
for (entry in agent.listFiles("docs").entries) println("${entry.name} ${entry.kind}")
val file = agent.readFile("docs/catalog.pdf") // base64 when binary
println("${file.encoding} ${file.size}")
agent.makeDir("archive")
agent.movePath("docs/old.pdf", "archive/old.pdf")
agent.removePath("archive")
```

The shared directory (`<install>/shared/`) is listed, and has directories
made and removed, under `client.files`, which also uploads images into an
agent's workspace (`uploadImage(agent, bytes)`) and returns the `[IMAGE:…]`
marker to attach them to a chat message.

## Personality

`SOUL.md`, `IDENTITY.md`, `USER.md`, `AGENTS.md`, `TOOLS.md`, `HEARTBEAT.md`
and `MEMORY.md` shape an agent's behavior. `personalityFiles()` lists them,
`personality(filename)` reads one, and
`writePersonality(filename, content, expectedMtimeMs)` replaces it (20 000
characters at most). Pass the `mtimeMs` you read to make the write fail with
409 instead of overwriting someone else's change; without it the write goes
through regardless. `personalityTemplates(PersonalityContext(userName = …))`
renders the defaults without writing them.

## Skills

Skills live in bundles (`skill_bundles.<alias>` in the configuration).
`client.skills` lists bundles and their skills and creates, reads, writes
and deletes (archives, or purges) a skill: front matter plus a Markdown body.
`client.agent(alias).skills()` shows what an agent actually loads, where
each skill comes from, and which the audit dropped.
