# Chatting with an agent

## Over the WebSocket

`client.chat(alias)` opens `/ws/chat` for agent `alias` and waits for the
gateway to confirm the session. The connection is a session
(`gw_<sessionId>`); pass `sessionId` to resume one, and `name` or `cwd` to
name it or set its working directory. Close the returned `ChatSession` when
done.

`ask` sends a message and waits for the reply:

```kotlin
val reply = client.chat("helper").use { it.ask("How many orders are pending?") }
println("${reply.fullResponse} ${reply.tokensUsed}")
```

To show progress, or to answer tool approvals, read the frames.
`frame.type` is one of `FrameType` and says which fields are set:

```kotlin
client.chat("helper").use { chat ->
    chat.send("Archive last month's invoices")
    chat.frames().collect { frame -> // ends when the gateway closes the chat
        when (frame.type) {
            FrameType.CHUNK -> render(frame.content)
            FrameType.TOOL_CALL -> println("running ${frame.name}")
            FrameType.APPROVAL_REQUEST -> chat.answerApproval(frame.requestId, "approve")
            FrameType.DONE -> println(frame.done?.costUsd)
            FrameType.ERROR -> throw TurnException(frame.code, frame.message)
        }
    }
}
```

`frames()` keeps reading until the gateway closes the chat; to stop after a
turn, read with `next()` (which returns `null` once the socket closed) and
break on `DONE`. Frames are read by one consumer at a time; sending works
any time. A message sent while a turn runs steers it. Closing the chat, or
`client.sessions.abort(id)`, cancels the running turn. `ask` throws a
failed turn as a `TurnException` with its `code` (`PROVIDER_ERROR`,
`SESSION_QUEUE_FULL`, `ABORTED`, …).

The token travels in the `Sec-WebSocket-Protocol` header as
`bearer.<token>`, beside the `zeroclaw.v1` subprotocol, and in the
`Authorization` header; the gateway accepts either. A refused handshake (a
bad token, an unknown agent) is a `WebSocketException` naming the status.

## One-shot: webhook and A2A

`client.webhook.send(message, WebhookOptions(…))` posts to `/webhook` and
returns the agent's reply in one HTTP call. Its options carry the `agent`,
the `secret` (`X-Webhook-Secret`) when the gateway sets one, an
`idempotencyKey` (a repeat answers `duplicate = true` without running again)
and a `sessionId` to keep calls in one conversation.

`client.webhook.a2aSend(alias, text)` does the same over the A2A protocol
(`message/send`) for agents published with `a2a.server.enabled`.
