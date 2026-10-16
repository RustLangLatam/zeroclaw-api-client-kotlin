# Timeouts and retries

## Timeouts

`ClientOptions.timeout` (default 60 s) bounds how long a request waits for
the gateway's answer, after which it throws a `RequestTimeoutException` and
its call is cancelled. It bounds the headers only, so a streamed body (the
event stream) is never cut. `null` waits without limit. A manual cron run
waits up to 660 s, as the gateway allows it 600. The same timeout bounds the
WebSocket handshake of a chat or a feed.

Cancelling the coroutine that makes a call cancels its HTTP request.

## Retries

Idempotent requests (GET, HEAD, OPTIONS, PUT, DELETE) are tried again after
a connection failure, a timeout, or a "try again" status (408, 429, 502,
503, 504), so a gateway that restarts does not fail a read. A POST or PATCH
is never retried: it could apply twice.

```kotlin
val client =
    Client(
        url,
        ClientOptions(
            token = token,
            timeout = 10.seconds,
            retry = RetryPolicy(maxRetries = 5, baseDelay = 250.milliseconds, maxDelay = 10.seconds, jitter = true),
        ),
    )
```

The wait before retry `n` is `baseDelay * 2^n`, capped at `maxDelay`; with
`jitter`, a random point in its upper half, so clients that failed together
do not retry together. A `Retry-After` on a 429 or 503 is honored up to
`maxDelay`. `retry = null` turns retries off.

## Your own OkHttp client

`ClientOptions.httpClient` replaces the `OkHttpClient` every request and
WebSocket goes through: add interceptors to log, trace, sign requests or
limit concurrency, or point it at a test server.

```kotlin
val http =
    OkHttpClient
        .Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // the event stream stays quiet for minutes
        .followRedirects(false)
        .addInterceptor { chain ->
            val started = System.nanoTime()
            chain.proceed(chain.request()).also { metrics.observe(System.nanoTime() - started) }
        }.build()
val client = Client(url, ClientOptions(token = token, httpClient = http))
```

Each try of a retried request goes through it. Rate limiting and circuit
breaking, built into the Python client, are left to such an interceptor
here.
