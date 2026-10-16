---
id: ADR-003
title: OkHttp, coroutines and kotlinx.serialization
date: 2026-10-16
status: accepted
relates-to:
  - build.gradle.kts
  - src/main/kotlin/io/github/rustlanglatam/zeroclaw/Client.kt
---

# ADR-003: OkHttp, Coroutines And kotlinx.serialization

## Context

The client is meant for Android apps as much as for JVM services. The JDK's
`java.net.http` is missing on Android, and Android has no built-in
WebSocket client. Most Kotlin projects already carry OkHttp, kotlinx.coroutines
and kotlinx.serialization.

## Decision

The client uses OkHttp for HTTP and WebSockets, kotlinx.coroutines for
`suspend` calls, timeouts and `Flow`s, and kotlinx.serialization-json for
types, and nothing else. OkHttp stays on the 4.12 line: OkHttp 5 asks Android
apps for a much newer compile SDK. Retries, backoff and timeouts are a few
dozen lines in `Client.kt`. The floor is Java 17 and Android API 26.

## Consequences

- An app that already uses these libraries adds no new artifacts; others add
  three well-known ones.
- No built-in rate limit or circuit breaker, unlike the Python client;
  callers who need them add an OkHttp interceptor.
- Java callers bridge the `suspend` functions themselves
  ([Java](../../guides/java.md)).
