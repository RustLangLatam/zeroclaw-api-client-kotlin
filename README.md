<h1 align="center">zeroclaw-api-client-kotlin</h1>

<p align="center">
  <strong>Your ZeroClaw, from Kotlin. On the JVM, on Android, with coroutines.</strong>
</p>

<p align="center">
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/RustLangLatam/zeroclaw-api-client-kotlin/ci.yml?branch=main&label=build" alt="Build Status" /></a>
  <a href="https://jitpack.io/#RustLangLatam/zeroclaw-api-client-kotlin"><img src="https://jitpack.io/v/RustLangLatam/zeroclaw-api-client-kotlin.svg" alt="JitPack" /></a>
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/LICENSE-APACHE"><img src="https://img.shields.io/badge/license-MIT%20OR%20Apache%202.0-blue.svg" alt="License" /></a>
  <img src="https://img.shields.io/badge/JVM-17%2B-e76f00" alt="JVM 17+" />
  <img src="https://img.shields.io/badge/Android-API%2026%2B-3ddc84" alt="Android API 26+" />
  <a href="https://github.com/zeroclaw-labs/zeroclaw"><img src="https://img.shields.io/badge/ZeroClaw-0.8.5-black" alt="ZeroClaw 0.8.5" /></a>
</p>

<p align="center">
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/introduction.md">Docs</a> ·
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/getting-started/index.md">Quick start</a> ·
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/guides/index.md">Guides</a> ·
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/guides/android.md">Android</a> ·
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/reference/endpoints.md">Endpoint map</a> ·
  <a href="https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/architecture/overview.md">Architecture</a> ·
  <a href="https://github.com/zeroclaw-labs/zeroclaw">ZeroClaw</a>
</p>

---

`zeroclaw-api-client` is a typed Kotlin client for the
[ZeroClaw](https://github.com/zeroclaw-labs/zeroclaw) gateway: its HTTP API,
its Server-Sent Events stream and its WebSocket chat. Every call is a
`suspend` function; live data is also a `Flow`. It runs on the JVM 17+ and on
Android API 26+, and Java code can use it too.

> An independent, community-maintained client. It is not affiliated with or
> endorsed by ZeroClaw Labs; ZeroClaw is a trademark of ZeroClaw Labs.

```kotlin
// settings.gradle.kts: repositories { mavenCentral(); maven("https://jitpack.io") }
dependencies {
    implementation("com.github.RustLangLatam:zeroclaw-api-client-kotlin:v0.1.0")
}
```

Until the library is on Maven Central (as
`io.github.rustlanglatam:zeroclaw-api-client`), JitPack builds it from the
release tag.

```kotlin
import io.github.rustlanglatam.zeroclaw.Client
import io.github.rustlanglatam.zeroclaw.ClientOptions

val client = Client("http://127.0.0.1:42617", ClientOptions(token = "zc_…"))

val status = client.system.status()
val channels = client.channels.list()
client.config.set("agents.helper.model", "gpt-5.4-mini")

val reply = client.chat("helper").use { it.ask("How many orders are pending?") }

println("${status.version} ${channels.size} ${reply.fullResponse}")
```

## Why an SDK

ZeroClaw is a self-hosted AI agent runtime. Everything an operator can do
with it (configure agents, models and channels, pair devices, run SOPs,
schedule jobs, read sessions and costs, talk to an agent) is exposed by its
gateway over HTTP and WebSockets. Calling that surface by hand from every
application that embeds ZeroClaw means repeating, in each of them:

- **the contract**: about 150 routes, a dozen response shapes for errors, a
  JSON Patch dialect for the configuration, an event stream whose frames have
  no names, a chat protocol with typed frames;
- **the edge cases**: secrets that are never returned, paths that must be
  encoded segment by segment, a gateway that drops connections while it
  reloads, routes that only exist with some build features;
- **the resilience**: timeouts that must not cut a stream, retries that must
  never repeat a write.

This library does it once, typed and tested, so applications talk to
ZeroClaw in terms of channels, agents and sessions instead of URLs and JSON.

## Features

- **The whole client surface** of the gateway, one property per area:

  | Property | Covers |
  |---|---|
  | `config` | per-field reads and writes, atomic JSON Patch, map aliases (agents, providers, channels), drift, catalogs, setup sections |
  | `pairing` | pairing codes, bearer tokens, paired devices |
  | `channels` | channel state, identity binding, relinking |
  | `chat()` | agent chat over WebSocket: streamed replies, tool calls, tool approvals |
  | `sessions`, `events` | transcripts, session state, the live Server-Sent Events stream |
  | `agent()`, `files`, `skills` | agent workspaces, personality files, shared files, image uploads, skill bundles |
  | `cron`, `memory`, `cost` | scheduled jobs, long-term memory, usage and cost |
  | `sops`, `canvas`, `quickstart` | SOP authoring, runs and approvals; canvases; the first-agent setup |
  | `system` | health, status, metrics, diagnostics, tools, logs, upgrades, reload |
  | `webhook`, `webauthn` | one-shot webhook and A2A messages; hardware-key ceremonies |

- **Coroutines first**: `suspend` calls that cancel their HTTP request when
  their coroutine is cancelled; the event stream, chat frames and feeds read
  one value at a time or as a `Flow`.
- **Android ready**: OkHttp 4.12 and API 26; see
  [Android](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/guides/android.md)
  for a gateway on the device itself.
- **Resilient by default**: answer timeouts that never cut a stream, and
  retries with jittered exponential backoff for idempotent calls only,
  honoring `Retry-After`. Pass your own `OkHttpClient` to add logging,
  tracing, signing or rate limiting.
- **Typed end to end**: `@Serializable` data classes that mirror the
  gateway's JSON, decoded leniently and checked against answers captured from
  a real gateway.
- **Typed errors**: every failure is a `ZeroClawException`; a gateway refusal
  is an `ApiException` that keeps its status, its explanation and its JSON
  body; `isNotFound`, `isUnauthorized`, `isRateLimited` and `isTransient`
  classify any error.
- **Safe by construction**: every path segment is percent-encoded, the token
  never appears in `toString()` or errors, nothing is logged.

## Technologies

| Concern | Choice |
|---|---|
| Language | Kotlin 2.2, bytecode for Java 17 |
| HTTP and WebSockets | [OkHttp](https://square.github.io/okhttp/) 4.12 (`zeroclaw.v1` subprotocol, `bearer.<token>`) |
| Concurrency | [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines): `suspend`, `withTimeout`, `Flow` |
| JSON | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), snake_case on the wire |
| Tests | kotlin.test on JUnit 5, OkHttp's `MockWebServer`, captured response vectors |
| Quality | [ktlint](https://pinterest.github.io/ktlint/), warnings as errors |
| Docs | KDoc and an [mdBook](https://rust-lang.github.io/mdBook/) book |
| Publishing | Maven Central (Central Portal), signed; JitPack from tags |

## Examples

Runnable programs under [`src/examples/kotlin/examples/`](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/tree/main/src/examples/kotlin/examples):

| Example | Shows |
|---|---|
| `Status.kt` | version, model and channels |
| `Events.kt` | following the live event stream, reconnecting |
| `Chat.kt` | streaming an agent's reply over the WebSocket |

```sh
ZEROCLAW_URL=http://127.0.0.1:42617 ZEROCLAW_TOKEN=zc_… ./gradlew -q runExample -Pexample=status
```

## Testing

- **Unit tests** for the logic: error mapping, URL encoding, SSE framing,
  chat frames, retries, backoff and timeouts.
- **HTTP-boundary tests** for every endpoint against `MockWebServer`:
  method, path, query, headers, body and result.
- **Response vectors**: answers captured from a real gateway (anonymized)
  must decode into the client's types and keep their values.
- **A live test** of every read against a running gateway, when one is given.

```sh
./gradlew build
```

## Compatibility

Targets the official ZeroClaw **0.8.5** gateway; see
[Compatibility](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/docs/book/src/reference/compatibility.md)
for the platforms it supports and the routes that need a build feature.

## Other languages

The same client, with the same surface, behavior and tests:

| Language | Repository | Install |
|---|---|---|
| Rust | [zeroclaw-api-client-rust](https://github.com/RustLangLatam/zeroclaw-api-client-rust) | `cargo add zeroclaw-api-client` |
| Go | [zeroclaw-api-client-go](https://github.com/RustLangLatam/zeroclaw-api-client-go) | `go get github.com/RustLangLatam/zeroclaw-api-client-go` |
| Python | [zeroclaw-api-client-python](https://github.com/RustLangLatam/zeroclaw-api-client-python) | `pip install zeroclaw-api-client` |
| TypeScript | [zeroclaw-api-client-ts](https://github.com/RustLangLatam/zeroclaw-api-client-ts) | `npm install zeroclaw-api-client` |

## Contributing

Work happens on branches with pull requests to `main`; see
[CONTRIBUTING.md](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/CONTRIBUTING.md)
and [AGENTS.md](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/AGENTS.md).

## License

Licensed under either of [Apache License, Version 2.0](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/LICENSE-APACHE)
or [MIT license](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/LICENSE-MIT),
at your option.
