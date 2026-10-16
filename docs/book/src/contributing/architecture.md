# Contributing

Read [`AGENTS.md`](https://github.com/RustLangLatam/zeroclaw-api-client-kotlin/blob/main/AGENTS.md)
first: it holds the rules every change follows. This page explains the most
common change, covering a gateway route.

## Adding or changing an endpoint

1. **Find the handler.** In the ZeroClaw build listed in
   [Compatibility](../reference/compatibility.md), find the route in
   `crates/zeroclaw-gateway/src/lib.rs` and read its handler: auth, query,
   body, every status it answers, and the struct or `json!` it returns. A
   route enters the client only once official ZeroClaw has it, or a pull
   request there adds it.
2. **Pick the file.** Routes belong to the file of their area (`Config.kt`,
   `Channels.kt`, …). A new area gets a new file, a class holding the
   client, and a property on `Client`.
3. **Write the function.** It is `suspend`, and its KDoc opens with the
   route (`` /** `POST /api/cron/{id}/run`: runs a job now and waits for it. */ ``).
   Pass the path as a list of segments to `c.call` (or `element`, `text`,
   `empty`), never a formatted string. Unwrap single-field envelopes with
   the `field` argument (`{"jobs": […]}` returns `List<CronJob>`).
4. **Write the types.** Mirror the gateway's fields in a `@Serializable` data
   class with camelCase properties; make anything optional or newer nullable
   with a `null` default; keep dynamic shapes `JsonElement`
   ([ADR-002](../architecture/decisions/ADR-002-dynamic-shapes-stay-json.md)).
5. **Test it.** Add a case to `EndpointsTest` (method, path, query, body,
   answer, result). For a new response type, add a captured vector (see
   [Testing](./testing.md)).
6. **Update the docs.** Regenerate the [endpoint map](../reference/endpoints.md)
   with `python3 scripts/endpoint-map.py --write` (CI fails while it is
   stale), and update the guide of the area. `python3 scripts/route-drift.py`
   then shows any gateway route still uncovered (CI runs it). For a new
   gateway build, pass its checkout to refresh `scripts/gateway-routes.txt`
   and commit the list with the client change.

## Checks

```sh
./gradlew ktlintCheck build
python3 scripts/endpoint-map.py --check
python3 scripts/route-drift.py
mdbook build docs/book
```

`./gradlew ktlintFormat` fixes formatting. CI runs the build on JDK 17 and 21.

## Branches and pull requests

Branch from `main` (`feat/*`, `fix/*`, `docs/*`) and open a PR with the
template filled in; never push to `main` directly. Use conventional
commits. Releases are tagged `vX.Y.Z` from `main` (the tag must match
`version` in `gradle.properties` and `VERSION` in `Version.kt`); the release
workflow publishes to Maven Central, and `CHANGELOG-next.md` becomes the
release notes.
