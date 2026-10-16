// Follows the gateway's live event stream, subscribing again when it drops
// (for example while the gateway reloads).
//
//     ZEROCLAW_URL=… ZEROCLAW_TOKEN=zc_… ./gradlew -q runExample -Pexample=events
package examples

import io.github.rustlanglatam.zeroclaw.Client
import io.github.rustlanglatam.zeroclaw.ClientOptions
import io.github.rustlanglatam.zeroclaw.isLog
import io.github.rustlanglatam.zeroclaw.isTransient
import io.github.rustlanglatam.zeroclaw.kind
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() =
    runBlocking {
        val client =
            Client(System.getenv("ZEROCLAW_URL") ?: "http://127.0.0.1:42617", ClientOptions(token = System.getenv("ZEROCLAW_TOKEN")))
        while (true) {
            try {
                client.events.subscribe().events().collect { event ->
                    println(if (event.isLog) "log   ${event["event"]}" else "event ${event.kind}")
                }
                println("stream closed; subscribing again")
            } catch (e: Exception) {
                if (!e.isTransient) throw e
                println("gateway unreachable ($e); retrying")
                delay(2000)
            }
        }
    }
