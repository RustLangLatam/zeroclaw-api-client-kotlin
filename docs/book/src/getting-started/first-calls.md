# First calls

```kotlin
import io.github.rustlanglatam.zeroclaw.Client
import io.github.rustlanglatam.zeroclaw.ClientOptions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = Client("http://127.0.0.1:42617", ClientOptions(token = System.getenv("ZEROCLAW_TOKEN")))

    // Public: no token needed.
    println(client.system.health().status)

    // Version, model, channels and process figures.
    val status = client.system.status()
    println("${status.version} ${status.model}")

    // Every configured channel and its state.
    for (channel in client.channels.list()) println("${channel.name} ${channel.status} ${channel.health}")

    // One configuration field.
    println(client.config.get("gateway.port").value)
}
```

A `Client` holds no connection of its own (OkHttp pools them), so it needs
no closing: make one per gateway and share it. Chats, feeds and event
streams are connections: close them when done (they are `Closeable`, so
`use { }` does it).

## Timeouts and retries

A request waits 60 seconds for the gateway's answer unless `timeout` says
otherwise; a manual cron run waits up to 660 s, as the gateway allows it
600. The timeout bounds the answer's headers only, so the live event stream
is never cut. Failed idempotent requests are retried three times with
backoff. See [Timeouts and retries](../guides/resilience.md).

## Examples

The repository ships runnable programs under `src/examples/kotlin/examples/`:

| Example | Shows |
|---|---|
| `Status.kt` | version, model and channels |
| `Events.kt` | following the live event stream, reconnecting |
| `Chat.kt` | streaming an agent's reply over the WebSocket |

```sh
ZEROCLAW_URL=http://127.0.0.1:42617 ZEROCLAW_TOKEN=zc_… ./gradlew -q runExample -Pexample=status
ZEROCLAW_URL=… ZEROCLAW_TOKEN=… ./gradlew -q runExample -Pexample=chat --args="helper Hello"
```
