# Getting Started

## Install

The library is `io.github.rustlanglatam:zeroclaw-api-client` on Maven Central:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// build.gradle.kts
dependencies {
    implementation("io.github.rustlanglatam:zeroclaw-api-client:0.1.1")
}
```

It brings OkHttp 4.12, kotlinx.coroutines and kotlinx.serialization-json
([why](../architecture/decisions/ADR-003-okhttp-coroutines-serialization.md)).
It needs Java 17 on the JVM, or Android API 26 (see [Android](../guides/android.md)).

## What you need from the gateway

- **Its URL.** The gateway listens on `127.0.0.1:42617` by default. Behind a
  reverse proxy, or with `[gateway] path_prefix`, use the full base URL
  (`https://host/zeroclaw`): the client appends routes to it.
- **A bearer token**, unless the gateway runs with pairing off. Tokens come
  from pairing: see [Connecting and pairing](./pairing.md).
- **Remote admin rights**, only for `/admin/*` calls from another host:
  those need `[gateway] allow_remote_admin = true` and pairing on.

Then make your [first calls](./first-calls.md). The snippets in this book,
past the first calls, run in a coroutine with a `client: Client` at hand.
