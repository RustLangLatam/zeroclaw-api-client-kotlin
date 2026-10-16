# Testing

Four layers of tests, each catching what the others cannot:

| Kind | Where | Catches |
|---|---|---|
| Unit | `ClientTest`, `ChatTest`, `EventsTest` | logic mistakes: URLs, errors, retries, timeouts, SSE framing, chat frames |
| HTTP boundary | `EndpointsTest`, against OkHttp's `MockWebServer` | wrong method, path, query, headers or body; what a call returns |
| Response vectors | `src/test/resources/vectors/*.json` + `VectorsTest` | types that do not match what a real gateway sends |
| Live | `LiveTest`, skipped without a gateway | anything a real gateway does differently today |

```sh
./gradlew test
```

`JavaUsageTest` keeps the Java usage in the [Java guide](../guides/java.md)
compiling.

## The mock gateway

`TestSupport.kt` starts a `MockWebServer` per test and a `Client` of it,
with retries that do not wait. The WebSocket tests upgrade a mock response
and play the gateway's side (sending frames, answering, closing).

## Response vectors

The vectors are the ones the Rust client captures from a real gateway with
its `scripts/capture-vectors.py` (anonymized: phone numbers, ids, UUIDs,
tokens, IPs and e-mails replaced; routes that hold people's words left
out). Each is `{"route", "build", "response"}`. `VectorsTest` serves each
answer to the matching call, checks the path, and checks that every field
the client's type keeps comes out with the captured value. Copy new vectors
from the Rust client and add a line for each.

## Live

`LiveTest` makes the read-only calls against a running gateway when one is
given, and fails on anything but success, a 404 (a route the build lacks) or
a 503 (a subsystem turned off):

```sh
ZEROCLAW_URL=http://127.0.0.1:42617 ZEROCLAW_TOKEN=zc_… ./gradlew test --tests '*LiveTest*' -i
```

Writes are not exercised live; the mock gateway covers them.
