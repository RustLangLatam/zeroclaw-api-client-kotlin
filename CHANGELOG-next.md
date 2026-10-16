# zeroclaw-api-client — next release

Changes since the start. They become the release notes of the next version.

## What's New

- The Kotlin client for the official ZeroClaw 0.8.5 gateway: the same 142
  routes as the Rust, Go, Python and TypeScript clients, as `suspend`
  functions grouped by area (`config`, `pairing`, `channels`, `chat`,
  `sessions`, `events`, `cron`, `memory`, `cost`, `sops`, `canvas`,
  `quickstart`, `skills`, `files`, `system`, `webhook`, `webauthn`).
- The live event stream (`events.subscribe()`) and the chat and feeds over
  WebSockets, read with `next()` or as a `Flow`.
- Typed errors (`ApiException`, `TransportException`,
  `RequestTimeoutException`, `DecodeException`, `WebSocketException`,
  `TurnException`) and their classification (`isNotFound`, `isTransient`, …).
- Answer timeouts that never cut a stream, and retries with jittered
  backoff for idempotent calls, honoring `Retry-After`.
- Runs on the JVM 17+ and Android API 26+; a builder for Java callers.

## Breaking Changes

None.
