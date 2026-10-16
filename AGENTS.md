# AGENTS.md - ZeroClaw API client for Kotlin

Core instructions for coding assistants working in this repository. Read `docs/book/src/contributing/architecture.md` before a non-trivial change.

## Single Source Of Truth

The gateway is the source of truth for every request and response shape. This SDK mirrors the router of the ZeroClaw build it targets (`crates/zeroclaw-gateway/src/lib.rs` in ZeroClaw) and never invents fields:

1. Before adding or changing a type, find the gateway handler and the struct (or `json!` literal) it serializes, and name it in the file's KDoc.
2. Shapes the gateway builds ad hoc, or that change with configuration (config sections, SOP definitions, catalogs), stay `JsonElement` instead of guessed data classes.
3. Optional and newer fields are nullable with a `null` default, so an older or newer gateway still decodes.
4. A route enters the client only once official ZeroClaw has it, or at least a pull request in zeroclaw-labs/zeroclaw adds it. Routes only a fork has stay out of `main` and its releases.

## Safety And Privacy

- Never commit secrets, tokens, credentials, personal data, or real identities. Tests use invented tokens and hosts.
- The bearer token never appears in `toString()`, errors, or URLs (on the WebSocket it travels in `Sec-WebSocket-Protocol` and `Authorization`).
- A library never prints, logs, or starts work that outlives a call: it throws typed exceptions (`ZeroClawException` subclasses) and leaves streams to the caller.
- Do not keep unused production code; remove it or use it.

## Working Rules

1. Read the owning file, its tests, and the matching gateway handler before editing.
2. One resource group per file (`Cron.kt`, `Config.kt`, …), reached from a `Client` property; path segments are passed as a list to `c.call`/`element`/`empty`, never formatted into a string, so ids are always encoded.
3. Keep one concern per PR. Avoid unrelated cleanup and do not mix broad formatting changes with functional changes.
4. Runtime dependencies are OkHttp 4.12, kotlinx.coroutines and kotlinx.serialization only ([ADR-003](docs/book/src/architecture/decisions/ADR-003-okhttp-coroutines-serialization.md)). A new dependency needs a reason in the PR.
5. Every endpoint gets a case in `EndpointsTest` (method, path, query, body, result). Logic gets unit tests. New response types get a captured vector in `src/test/resources/vectors/` and a line in `VectorsTest`.
6. Work on a non-`main` branch (`feat/*`, `fix/*`, `docs/*`), open a PR to `main`, and never push directly to `main`. Releases are tagged `vX.Y.Z` from `main`; the release workflow publishes to Maven Central.
7. Use conventional commits. Commits are authored by the repository owner; do not add bot or assistant attribution footers or files.
8. Name no application, company or deployment that uses the client: not in code, docs, test vectors, commits or PRs. Say "an application".

## User-Facing Text

- Code, comments, errors, and documentation are in English.
- English Markdown under `docs/book/` is the documentation source of truth; KDoc covers each public name.

## Validation

```bash
./gradlew ktlintCheck build compileExamplesKotlin
python3 scripts/endpoint-map.py --check
python3 scripts/route-drift.py
```

Docs changes also build the book: `mdbook build docs/book`.
