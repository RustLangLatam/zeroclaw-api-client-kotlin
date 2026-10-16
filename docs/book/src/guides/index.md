# Guides

Each guide covers one area of the gateway and the file that calls it:

| Guide | File | Property |
|---|---|---|
| [Configuration](./configuration.md) | `Config.kt` | `client.config` |
| [Agents, personality and skills](./agents.md) | `Agents.kt`, `Skills.kt`, `Files.kt` | `client.agent(alias)`, `client.skills`, `client.files` |
| [Channels](./channels.md) | `Channels.kt` | `client.channels` |
| [Chatting with an agent](./chat.md) | `Chat.kt`, `Webhook.kt` | `client.chat(alias, …)`, `client.webhook` |
| [Sessions and live events](./events.md) | `Sessions.kt`, `Events.kt` | `client.sessions`, `client.events` |
| [Cron, memory and cost](./operations.md) | `Cron.kt`, `Memory.kt`, `Cost.kt`, `System.kt` | `client.cron`, `client.memory`, `client.cost`, `client.system` |
| [SOPs](./sops.md) | `Sops.kt`, `Canvas.kt`, `Quickstart.kt` | `client.sops`, `client.canvas`, `client.quickstart` |
| [Android](./android.md) | — | — |
| [Java](./java.md) | — | `Client.builder(url)` |
| [Timeouts and retries](./resilience.md) | `Client.kt` | `Client(url, ClientOptions(…))` |
| [Errors](./errors.md) | `Errors.kt` | — |

Pairing is covered in [Getting Started](../getting-started/pairing.md);
WebAuthn (`client.webauthn`) only carries the JSON of a hardware-key
ceremony run elsewhere (the Credential Manager on Android).

The snippets run in a coroutine with a `client: Client` at hand; names come
from the package `io.github.rustlanglatam.zeroclaw`.
