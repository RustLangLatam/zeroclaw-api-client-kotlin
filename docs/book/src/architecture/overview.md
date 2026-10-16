# Architecture

The library is one Gradle module, package `io.github.rustlanglatam.zeroclaw`,
with one source file per area of the gateway under `src/main/kotlin/`.

## The client

`Client` (`Client.kt`) holds the base URL, the bearer token and its options
(`ClientOptions`: `token`, `timeout`, `retry`, `httpClient`). It holds no
connection: OkHttp pools them, so a client needs no closing and is safe to
share across coroutines and threads. `withToken` returns a client with the
same options and another token; `Client.builder` builds one from Java.

All HTTP goes through a few internal functions in `Client.kt`:

- `url(segments, query)` appends **path segments**, each percent-encoded by
  OkHttp's `HttpUrl`, to the base URL. Ids are never formatted into a path
  string, so an id holding `/` or `?` stays one segment;
- `send` attaches the bearer token and the JSON body, bounds the wait for
  the answer's headers with `withTimeout`, retries idempotent requests, and
  turns a non-success status into an `ApiException` that keeps the gateway's
  message and JSON body;
- `call<T>` decodes the answer (or one field of its envelope, such as `jobs`
  of `{"jobs": […]}`) with kotlinx.serialization; `element` returns it as a
  `JsonElement`, `text` and `empty` read it raw.

## Resource properties

Each file defines a small class holding the client (`ConfigApi`,
`ChannelsApi`, …) whose `suspend` functions are the endpoints, and `Client`
exposes it as a property (`client.config`, `client.channels`, …).
`client.agent(alias)` also carries the agent alias. A function's KDoc opens
with the route it calls, which the [endpoint map](../reference/endpoints.md)
is generated from.

## Streams

Two kinds of live data are read by the caller, one value at a time (the
client starts no background work for them):

- **Server-Sent Events** (`client.events.subscribe()`): `Events.kt` reads
  the response body line by line on `Dispatchers.IO`, splits it into frames
  and decodes their JSON; `EventStream.next()` returns `null` at the end and
  `events()` is a `Flow`;
- **WebSockets** (`Chat.kt`, on OkHttp's WebSocket): a socket is opened with
  ZeroClaw's `zeroclaw.v1` subprotocol and the token as `bearer.<token>`
  (and in `Authorization`). Incoming text frames land in a `Channel`; the
  chat reads typed `ChatFrame`s, the canvas and SOP-run feeds are `Feed`s of
  `JsonElement`s, both with `next()` and `frames()`.

## Types

Response types are `@Serializable` data classes that mirror the gateway's
serialized structs and `json!` literals, field for field. Properties are
camelCase in Kotlin and snake_case on the wire, through the
`JsonNamingStrategy.SnakeCase` of `ZeroClawJson`. Unknown fields are
ignored; optional or newer fields are nullable with a `null` default
([ADR-004](./decisions/ADR-004-lenient-typed-decoding.md)). Shapes that are
dynamic or versioned with ZeroClaw stay `JsonElement`
([ADR-002](./decisions/ADR-002-dynamic-shapes-stay-json.md)).
