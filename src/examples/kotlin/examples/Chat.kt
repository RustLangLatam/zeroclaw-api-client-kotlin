// Streams an agent's reply over the chat WebSocket.
//
//     ZEROCLAW_URL=… ZEROCLAW_TOKEN=zc_… ./gradlew -q runExample -Pexample=chat --args="helper 'Hello!'"
package examples

import io.github.rustlanglatam.zeroclaw.Client
import io.github.rustlanglatam.zeroclaw.ClientOptions
import io.github.rustlanglatam.zeroclaw.FrameType
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) =
    runBlocking {
        val agent = args.getOrNull(0) ?: "helper"
        val message = args.getOrNull(1) ?: "Hello!"
        val client =
            Client(System.getenv("ZEROCLAW_URL") ?: "http://127.0.0.1:42617", ClientOptions(token = System.getenv("ZEROCLAW_TOKEN")))
        client.chat(agent).use { chat ->
            chat.send(message)
            while (true) {
                val frame = chat.next() ?: break
                when (frame.type) {
                    FrameType.CHUNK -> print(frame.content)
                    FrameType.TOOL_CALL -> println("\n[tool ${frame.name}]")
                    FrameType.DONE -> {
                        println("\n(${frame.done?.tokensUsed ?: 0} tokens)")
                        break
                    }
                    FrameType.ERROR -> error("${frame.code}: ${frame.message}")
                }
            }
        }
    }
