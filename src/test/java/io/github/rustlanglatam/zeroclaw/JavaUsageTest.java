package io.github.rustlanglatam.zeroclaw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

/** The client from Java: built with its builder, called through runBlocking. */
class JavaUsageTest {
    @Test
    void javaBuildsAClientAndCallsIt() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\",\"paired\":true,\"require_pairing\":true}"));
            Client client = Client.builder(server.url("/").toString()).token("t").timeoutMillis(5_000).build();
            Health health = BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE, (scope, cont) -> client.getSystem().health(cont));
            assertEquals("ok", health.getStatus());
            assertTrue(health.getPaired());
            assertEquals("Bearer t", server.takeRequest().getHeader("Authorization"));
        }
    }
}
