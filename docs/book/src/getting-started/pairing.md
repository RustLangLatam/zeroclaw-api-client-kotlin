# Connecting and pairing

A ZeroClaw gateway with pairing on (the default) answers only clients that
send a paired bearer token: `Authorization: Bearer zc_<64 hex>`. A client
gets one by exchanging a six-digit pairing code, which is single use and
valid for ten minutes.

## Getting a code

- **First run:** the daemon prints a code when it starts and no client is
  paired yet; `client.pairing.initialCode()` also returns it until the
  first pairing.
- **From a paired client:** `client.pairing.newCode()` mints one for another
  device.
- **On the gateway host:** `client.pairing.adminCode()` and
  `client.pairing.adminNewCode(rotate)` (loopback only); `rotate` can also
  revoke every token (`"all"`) or one device's.

## Exchanging it

```kotlin
val anonymous = Client("http://127.0.0.1:42617")
val paired = anonymous.pairing.pair("123456", deviceName = "billing-service", deviceType = "server")
// Store paired.token as a secret; it is shown only once.
val client = anonymous.withToken(paired.token)
```

`withToken` returns a client for the same gateway and options. A wrong or
expired code is refused with 400; too many attempts are rate-limited with
429 (`e.isRateLimited`), with a `retry_after` field in the error body
(`ApiException.body`).

## Managing devices

`client.pairing.devices()` lists the paired devices, `revokeDevice(id)`
revokes one and its token, and `rotateDeviceToken(id)` revokes it and mints
a code for it to pair again.

## Keeping the token safe

The client never shows its token: `client.toString()` and
`ClientOptions.toString()` give only the base URL and whether it is
authenticated, and the token never appears in an error or a URL. On the
WebSocket it travels in the `Sec-WebSocket-Protocol` header and the
`Authorization` header, not in the query string. The token is an operator
credential for the whole gateway: on Android keep it in the Keystore, never
in resources or `BuildConfig` of a published app.
