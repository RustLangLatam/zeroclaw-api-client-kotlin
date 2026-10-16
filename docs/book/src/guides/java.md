# Java

The client is written in Kotlin and calls are `suspend` functions, which
Java sees with an extra `Continuation` parameter. Build a client with its
builder and run a call through `kotlinx.coroutines`' `runBlocking`, on a
thread that may block:

```java
import io.github.rustlanglatam.zeroclaw.Client;
import io.github.rustlanglatam.zeroclaw.Health;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;

Client client = Client.builder("http://127.0.0.1:42617")
    .token(System.getenv("ZEROCLAW_TOKEN"))
    .timeoutMillis(10_000)
    .maxRetries(3)
    .build();

Health health = BuildersKt.runBlocking(
    EmptyCoroutineContext.INSTANCE,
    (scope, continuation) -> client.getSystem().health(continuation));
System.out.println(health.getStatus());
```

Properties are getters (`client.getConfig()`, `client.getCron()`), data
classes have getters and `copy`, and the builders of `PatchOp` and
`SopDecision` are static (`PatchOp.replace(path, value)`). Errors are the
same exceptions, unwrapped by `runBlocking`. `JavaUsageTest` in the test
sources keeps this example compiling.

For non-blocking Java, add `kotlinx-coroutines-jdk8` and wrap calls with
`FutureKt.future` to get a `CompletableFuture`.
