# Sessions and live events

## Sessions

Every conversation is a session: web chats are `gw_<id>`, channel
conversations `whatsapp_<channel>_<peer>…`. `client.sessions.list()` returns
them with their agent and channel; `messages(id)` the transcript (tool calls
and results included, as JSON text); `state(id)` whether a turn is running;
`running()` only the gateway's own (web chat) sessions.

`addMessage(id, content)` appends an assistant message (shown to chat
clients, with no agent turn); `rename`, `abort` and `delete` do what they
say.

## The live event stream

`client.events.subscribe()` follows `/api/events`, the gateway's
Server-Sent Events stream. Read it as a `Flow` with `events()`, or with
`next()` until it returns `null`:

```kotlin
client.events.subscribe().use { stream ->
    while (true) {
        val event =
            try {
                stream.next() ?: break // the gateway closed it: subscribe again
            } catch (e: DecodeException) {
                continue // a frame that is not JSON; the stream goes on
            }
        if (event.kind == "agent_end") println("turn cost ${event["cost_usd"]}")
        else if (event.isLog) println("log ${event["event"]}")
    }
}
```

An event is a `JsonObject` of the frame's top-level fields. Frames have no
SSE event name; `event.kind` reads their `type`. Observer events
(`llm_request`, `tool_call`, `tool_call_start`, `agent_start`, `agent_end`,
`error`, `history_trimmed`) carry `source: "observability"`; log events
carry `@timestamp` instead of a `type`. The stream ends when the gateway
closes it (on reload, for instance): subscribe again;
`examples/Events.kt` shows the loop. `client.events.history()` returns the
latest observer events, kept in memory.

The client's timeout bounds only the wait for the stream to open; `close()`
ends it. Reading blocks a thread of `Dispatchers.IO`; cancel a reader by
closing the stream.
