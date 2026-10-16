// The gateway's version, model and channels.
//
//     ZEROCLAW_URL=http://127.0.0.1:42617 ZEROCLAW_TOKEN=zc_… ./gradlew -q runExample -Pexample=status
package examples

import io.github.rustlanglatam.zeroclaw.Client
import io.github.rustlanglatam.zeroclaw.ClientOptions
import kotlinx.coroutines.runBlocking

fun main() =
    runBlocking {
        val client =
            Client(System.getenv("ZEROCLAW_URL") ?: "http://127.0.0.1:42617", ClientOptions(token = System.getenv("ZEROCLAW_TOKEN")))
        val status = client.system.status()
        println("ZeroClaw ${status.version}, model ${status.model}")
        for (channel in client.channels.list()) {
            println("  ${channel.name}: ${channel.status} (${channel.health})")
        }
    }
