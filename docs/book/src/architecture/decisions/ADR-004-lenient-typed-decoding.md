---
id: ADR-004
title: Types decode leniently, required fields are checked
date: 2026-10-16
status: accepted
relates-to:
  - src/main/kotlin/io/github/rustlanglatam/zeroclaw/Client.kt
  - src/test/kotlin/io/github/rustlanglatam/zeroclaw/VectorsTest.kt
---

# ADR-004: Types Decode Leniently, Required Fields Are Checked

## Context

The gateway evolves between builds: fields appear, optional fields come and
go. A client that rejects anything unexpected breaks on every upgrade; one
that checks nothing returns objects with missing data.

## Decision

Answers are decoded with kotlinx.serialization (`ZeroClawJson`): unknown
fields are ignored, fields the gateway marks optional or added recently are
nullable with a `null` default, and fields every build sends are required. A
missing required field, or a value of the wrong type, is a
`DecodeException`. The captured response vectors in
`src/test/resources/vectors/` are decoded in `VectorsTest`, and every field
the types keep must come out with the captured value.

## Consequences

- A newer gateway that adds fields keeps working; one that drops a required
  field fails loudly instead of handing back half an object.
- Fields the client does not model are not available on the data classes;
  callers who need them use the `JsonElement` calls or decode the answer
  themselves.
- Field names keep the gateway's snake_case on the wire, as in the sibling
  clients, and are camelCase in Kotlin.
