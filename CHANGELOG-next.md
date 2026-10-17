# zeroclaw-api-client — next release

Changes since v0.1.0. They become the release notes of the next version.

## What's New

- A refused WebSocket handshake keeps its HTTP status: `WebSocketException.status`,
  so `statusCode`, `isUnauthorized` and `isTransient` work on it (before, the status was
  only in the message).

## Breaking Changes

None.
