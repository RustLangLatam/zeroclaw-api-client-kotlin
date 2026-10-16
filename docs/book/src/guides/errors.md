# Errors

Every failure throws a `ZeroClawException` subclass. Catch the class you
care about:

| Exception | When |
|---|---|
| `ApiException` (`status`, `reason`, `body`) | The gateway answered with a non-success status. `reason` is its explanation (the `error` or `message` field, or the body text); `body` keeps the JSON error body. Its message reads `gateway answered <status>: <reason>`. |
| `TransportException` | No HTTP answer: connection refused, DNS or TLS failure, a broken connection. Its `cause` is OkHttp's `IOException`. |
| `RequestTimeoutException` | The gateway did not answer in time, after any retries. |
| `DecodeException` | A successful answer did not have the expected shape (a required field missing, a wrong type). |
| `WebSocketException` | A WebSocket failed, or its handshake was refused (the status is in the message). |
| `TurnException` (`code`) | A chat turn ended in an `error` frame, or was aborted (`ABORTED`). |
| `InvalidUrlException` | The base URL is not an absolute `http`/`https` URL (thrown by the constructor). |

The extension properties `statusCode`, `isNotFound`, `isUnauthorized` (401
and 403), `isRateLimited` (429) and `isTransient` (no connection, a timeout,
or 408, 429, 502, 503, 504: worth trying again later) answer the usual
questions on any `Throwable`. Idempotent calls were already retried before a
transient error reaches you; see [Timeouts and retries](./resilience.md).

## The gateway's error bodies

The gateway does not use one error shape, and the client reads all of them
into `reason`:

- `{"error": "…"}`, most routes, sometimes with `retry_after` (seconds) on
  429;
- `{"code": "…", "message": "…", "path": "…", "op_index": n}` on
  configuration routes (`path_not_found`, `value_type_mismatch`,
  `config_changed_externally`, …) and uploads;
- `{"channel": "…", "error": "…"}` on channel actions;
- plain text when the request is refused before its handler runs (a
  malformed body, a body over 64 KiB, the device endpoints' 401), and an
  empty body on the gateway's request timeout (408).

Read further fields from `body`:

```kotlin
try {
    client.config.set("gateway.port", "not a number")
} catch (e: ApiException) {
    if (e.status == 400) println((e.body as? JsonObject)?.get("code")) // "value_type_mismatch"
}
```
