# Android

The client runs on Android from API 26 (Android 8.0). It needs the
`INTERNET` permission and, to reach a gateway over plain `http` (a daemon on
the device itself at `127.0.0.1`, or one on the LAN), a network security
configuration that allows cleartext for that host.

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<application android:networkSecurityConfig="@xml/network_security_config" … />

<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

## Calling it

Calls are `suspend` functions: run them from a `viewModelScope`,
`lifecycleScope` or your own scope, never on the main thread with
`runBlocking`. The client's own I/O runs on OkHttp's threads, and the event
stream reads on `Dispatchers.IO`.

```kotlin
class AssistantViewModel(private val client: Client) : ViewModel() {
    val reply = MutableStateFlow("")

    fun ask(text: String) =
        viewModelScope.launch {
            reply.value = client.chat("helper").use { it.ask(text) }.fullResponse
        }
}
```

Make one `Client` per gateway for the app (an OkHttp client underneath
pools connections) and pass it around. To share your app's own
`OkHttpClient` (interceptors, certificate pinning), pass it as
`ClientOptions(httpClient = …)`; give it no read timeout, or the event
stream and long cron runs are cut, and no redirects.

## A gateway on the device

With ZeroClaw running on the phone itself, use `http://127.0.0.1:42617`.
Bind the gateway to `127.0.0.1` only, and keep the token in the Android
Keystore (for example `EncryptedSharedPreferences`), never in resources or
`BuildConfig` of a published app. Some devices resolve `localhost` to `::1`
first: write `127.0.0.1` explicitly.

## Why OkHttp 4

The client depends on OkHttp 4.12, the line Android apps can use with any
compile SDK; OkHttp 5 asks for a much newer one. An app that already uses
OkHttp 4 shares the same artifact.
