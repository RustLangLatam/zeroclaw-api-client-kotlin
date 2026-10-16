# Compatibility

## ZeroClaw versions

The client targets the official ZeroClaw **0.8.5** gateway
([zeroclaw-labs/zeroclaw](https://github.com/zeroclaw-labs/zeroclaw), tag
`v0.8.5`), whose router (its routes are in `scripts/gateway-routes.txt`) is
the [source of truth](../architecture/decisions/ADR-001-gateway-is-the-source-of-truth.md)
for every request and response shape here. A route enters the client once
official ZeroClaw has it, or at least a pull request that adds it.

Some routes exist only when the gateway is built with a feature: WebAuthn
(`webauthn`) and A2A (`a2a`). Without it they answer 404.

Answers decode leniently
([ADR-004](../architecture/decisions/ADR-004-lenient-typed-decoding.md)):
fields a gateway adds are ignored, and optional or newer fields are nullable
with a `null` default, so a nearby build still decodes.

## Platforms

| Platform | Supported | Notes |
|---|---|---|
| JVM | Java 17 and newer | CI tests 17 and 21 |
| Android | API 26 and newer | see [Android](../guides/android.md) |
| Kotlin | 2.0 and newer to read the library's metadata | built with Kotlin 2.2 |
| Java callers | Java 17 and newer | see [Java](../guides/java.md) |

Runtime dependencies: OkHttp 4.12, kotlinx.coroutines 1.10 and
kotlinx.serialization-json 1.9
([ADR-003](../architecture/decisions/ADR-003-okhttp-coroutines-serialization.md)).
