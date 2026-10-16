# ZeroClaw API Client

`zeroclaw-api-client` is a typed Kotlin client for the
[ZeroClaw](https://github.com/zeroclaw-labs/zeroclaw) gateway: the HTTP and
WebSocket surface a ZeroClaw daemon exposes for its dashboard, its CLI and
third-party tooling. It is the Kotlin port of
[zeroclaw-api-client-rust](https://github.com/RustLangLatam/zeroclaw-api-client-rust),
[zeroclaw-api-client-go](https://github.com/RustLangLatam/zeroclaw-api-client-go),
[zeroclaw-api-client-python](https://github.com/RustLangLatam/zeroclaw-api-client-python)
and [zeroclaw-api-client-ts](https://github.com/RustLangLatam/zeroclaw-api-client-ts),
and covers the same routes.

It runs on the JVM 17+ and on Android (API 26+), with coroutines: every call
is a `suspend` function, and live data (the event stream, chat frames, feeds)
is also available as a `Flow`. Java code can build a client and call it
through its own coroutine bridge.

With it an application can do everything the gateway allows a client to do:

- read and change the configuration, one field at a time or in atomic
  batches, including agents, providers and channels;
- pair new clients and manage the paired devices;
- see channels and their health, and manage who may talk to them;
- chat with an agent, streaming its reply, tool calls and approval requests;
- follow the live event stream and read session transcripts;
- schedule cron jobs, store and recall memory, and read usage and cost;
- edit agent workspaces, personality files, skills and SOPs, and run SOPs;
- check health, run diagnostics, read logs and upgrade the daemon.

It is a library: it never prints, logs or starts work that outlives a call.
Every call returns a typed result or throws a typed [error](./guides/errors.md)
carrying the gateway's own explanation.

This is an independent, community-maintained client. It is not affiliated
with or endorsed by ZeroClaw Labs.

## How it is organized

A [`Client`](./architecture/overview.md) holds the gateway URL and the bearer
token. Each group of endpoints is reached through a property, so call sites
read like the gateway's routes:

```kotlin
val client = Client("http://127.0.0.1:42617", ClientOptions(token = "zc_…"))
val channels = client.channels.list()
client.config.set("agents.helper.model", "gpt-5.4-mini")
val reply = client.chat("helper").use { it.ask("Hi") }
println("${channels.size} ${reply.fullResponse}")
```

Every call suspends, and a failure throws a `ZeroClawException`. Start with
[Getting Started](./getting-started/index.md). The
[endpoint map](./reference/endpoints.md) lists every route and the function
that calls it.
