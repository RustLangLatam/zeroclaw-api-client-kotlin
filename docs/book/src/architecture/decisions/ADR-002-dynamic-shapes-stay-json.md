---
id: ADR-002
title: Dynamic shapes stay JSON
date: 2026-10-16
status: accepted
relates-to:
  - src/main/kotlin/io/github/rustlanglatam/zeroclaw/Sops.kt
  - src/main/kotlin/io/github/rustlanglatam/zeroclaw/Config.kt
---

# ADR-002: Dynamic Shapes Stay JSON

## Context

Some gateway payloads are large, change with every ZeroClaw release, or
depend on the configuration: the whole-config snapshot and its JSON Schema,
SOP definitions, graphs and run overlays, quickstart submissions, skill front
matter, log and event frames, WebAuthn ceremonies.

## Decision

Those stay `JsonElement` (an event is a `JsonObject`), and requests that send
them take `JsonElement`. Stable, small shapes (channels, sessions, cron jobs,
memory entries, cost, devices, …) get data classes.

## Consequences

- A ZeroClaw upgrade that reshapes an SOP or a config section does not break
  the client.
- Callers of the JSON-shaped calls read the fields they need, or decode them
  into their own classes with `ZeroClawJson`; the KDoc of each call lists its
  top-level fields.
- A shape can move from `JsonElement` to a data class later, when it has
  stabilized upstream.
