package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A successful pairing. */
@Serializable
data class Paired(
    /** Always true. */
    val paired: Boolean,
    /** Whether the token was saved in the gateway's configuration. */
    val persisted: Boolean? = null,
    /** The bearer token. Keep it secret. */
    val token: String,
    /** The gateway's note. */
    val message: String? = null,
)

/** A pairing code and whether pairing is required. */
@Serializable
data class PairingCode(
    /** Whether the request succeeded. */
    val success: Boolean? = null,
    /** Whether the gateway requires pairing. */
    val pairingRequired: Boolean,
    /** The six-digit code, when one is pending and may be shown. */
    val pairingCode: String? = null,
    /** The gateway's note. */
    val message: String? = null,
)

/** A paired device. */
@Serializable
data class Device(
    /** The device id. */
    val id: String,
    /** The name given at pairing. */
    val name: String? = null,
    /** The type given at pairing (`legacy` for tokens older than the registry). */
    val deviceType: String? = null,
    /** When it paired (RFC 3339). */
    val pairedAt: String,
    /** When it was last seen (RFC 3339). */
    val lastSeen: String,
    /** The address it paired from. */
    val ipAddress: String? = null,
    /** The advertised capabilities. */
    val capabilities: List<String>? = null,
)

/** The pairing and paired devices endpoints, from [Client.pairing]. */
class PairingApi internal constructor(
    private val c: Client,
) {
    /**
     * `POST /api/pair`: exchanges a pairing code for a bearer token,
     * registering this client as a device. Public.
     */
    suspend fun pair(
        code: String,
        deviceName: String? = null,
        deviceType: String? = null,
    ): Paired =
        c.call(
            "POST",
            listOf("api", "pair"),
            json =
                buildJsonObject {
                    put("code", code)
                    put("device_name", deviceName?.ifEmpty { null })
                    put("device_type", deviceType?.ifEmpty { null })
                },
        )

    /** `POST /pair`: pairs the original way, with the code in the `X-Pairing-Code` header. Public. */
    suspend fun pairLegacy(code: String): Paired = c.call("POST", listOf("pair"), headers = mapOf("X-Pairing-Code" to code))

    /** `GET /pair/code`: the first-run pairing code, shown only while no client is paired yet. Public. */
    suspend fun initialCode(): PairingCode = c.call("GET", listOf("pair", "code"))

    /** `POST /api/pairing/initiate`: mints a new pairing code (replacing a pending one) so another device can pair. */
    suspend fun newCode(): String = c.call("POST", listOf("api", "pairing", "initiate"), "pairing_code")

    /** `GET /admin/paircode`: the pending pairing code. Loopback only. */
    suspend fun adminCode(): PairingCode = c.call("GET", listOf("admin", "paircode"))

    /**
     * `POST /admin/paircode/new`: mints a pairing code. Loopback only.
     * `rotate` revokes first: `all` every token and device, otherwise the
     * device with that id.
     */
    suspend fun adminNewCode(rotate: String? = null): PairingCode =
        c.call("POST", listOf("admin", "paircode", "new"), query = mapOf("rotate" to rotate))

    /** `GET /api/devices`: the paired devices. */
    suspend fun devices(): List<Device> = c.call("GET", listOf("api", "devices"), "devices")

    /** `DELETE /api/devices/{id}`: revokes a device and its token; it may be this client's own. */
    suspend fun revokeDevice(id: String) = c.empty("DELETE", listOf("api", "devices", id))

    /**
     * `POST /api/devices/{id}/token/rotate`: revokes a device's token and
     * mints a pairing code for it to pair again. The code is `null` when one
     * was already pending or pairing is off.
     */
    suspend fun rotateDeviceToken(id: String): String? {
        val envelope = c.element("POST", listOf("api", "devices", id, "token", "rotate"))
        val code = (envelope as? JsonObject)?.get("pairing_code") as? JsonPrimitive
        return code?.takeIf { it.isString }?.content
    }

    /** `POST /api/devices/me/capabilities`: replaces the capabilities this client's device advertises and returns them. */
    suspend fun setCapabilities(capabilities: List<String>): List<String> =
        c.call(
            "POST",
            listOf("api", "devices", "me", "capabilities"),
            "capabilities",
            json = buildJsonObject { putJsonArray("capabilities") { capabilities.forEach { add(it) } } },
        )
}
