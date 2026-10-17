# zeroclaw-api-client — next release

Changes since v0.1.1. They become the release notes of the next version.

## What's New

None yet.

## Fixes

- `MemoryEntry.importance` is a `Double`: the gateway sends a fraction (for example `0.5`), which failed to decode as a `Long`, so `memory.search()` threw on any entry that had one.

## Breaking Changes

- `MemoryEntry.importance` changes from `Long?` to `Double?` (see Fixes). Code that read it as a `Long` must read a `Double`; it could not decode a real answer before.
