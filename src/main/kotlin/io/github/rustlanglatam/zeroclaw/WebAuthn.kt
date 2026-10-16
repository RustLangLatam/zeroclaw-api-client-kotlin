package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The hardware-key registration and assertion endpoints (`/api/webauthn`),
 * from [Client.webauthn]. They exist only when the gateway is built with the
 * `webauthn` feature; otherwise they answer 404.
 */
class WebAuthnApi internal constructor(
    private val c: Client,
) {
    /** `POST /api/webauthn/register/start`: the creation challenge for a new key of `userId`. */
    suspend fun registerStart(
        userId: String,
        userName: String,
    ): JsonElement =
        c.element(
            "POST",
            listOf("api", "webauthn", "register", "start"),
            json =
                buildJsonObject {
                    put("user_id", userId)
                    put("user_name", userName)
                },
        )

    /**
     * `POST /api/webauthn/register/finish`: sends the authenticator's answer
     * and returns the registered key. `credential` holds `challenge`, `id`,
     * `attestation_object`, `client_data_json` and an optional `label`; the
     * answer is `{credential_id, label, registered_at}`.
     */
    suspend fun registerFinish(credential: JsonElement): JsonElement =
        c.element("POST", listOf("api", "webauthn", "register", "finish"), json = credential)

    /** `POST /api/webauthn/auth/start`: the assertion challenge for `userId`. */
    suspend fun authStart(userId: String): JsonElement =
        c.element("POST", listOf("api", "webauthn", "auth", "start"), json = buildJsonObject { put("user_id", userId) })

    /**
     * `POST /api/webauthn/auth/finish`: sends the authenticator's assertion.
     * `assertion` holds `challenge`, `id`, `authenticator_data`,
     * `client_data_json` and `signature`. Refused with 401 when it does not
     * verify.
     */
    suspend fun authFinish(assertion: JsonElement): JsonElement =
        c.element("POST", listOf("api", "webauthn", "auth", "finish"), json = assertion)

    /** `GET /api/webauthn/credentials`: the keys of `userId`. */
    suspend fun credentials(userId: String): JsonElement =
        c.element("GET", listOf("api", "webauthn", "credentials"), query = mapOf("user_id" to userId))

    /** `DELETE /api/webauthn/credentials/{id}`: removes key `credentialId` of `userId`. */
    suspend fun deleteCredential(
        userId: String,
        credentialId: String,
    ) = c.empty("DELETE", listOf("api", "webauthn", "credentials", credentialId), query = mapOf("user_id" to userId))
}
