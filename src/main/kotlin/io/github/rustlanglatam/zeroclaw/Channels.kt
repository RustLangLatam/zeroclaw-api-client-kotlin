package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A configured channel. */
@Serializable
data class Channel(
    /** `type.alias`, the name the other calls take. */
    val name: String,
    /** The channel type (`whatsapp`, `telegram`, …). */
    val type: String,
    /** The alias. */
    val alias: String,
    /** The agent it belongs to. */
    val owningAgent: String? = null,
    /** Whether it is enabled. */
    val enabled: Boolean,
    /** Whether this build includes its type. */
    val compiled: Boolean,
    /** `active`, `inactive`, `error`, `unknown` or `not_compiled`. */
    val status: String,
    /** `healthy`, `degraded`, `down` or `unavailable`. */
    val health: String,
    /**
     * What it still needs (`enabled`, `bound_to_agent`, `authenticated`,
     * `listening`, each `ready`/`missing`/`unknown`, plus notes).
     */
    val readiness: JsonElement? = null,
    /** The number of messages the channel has carried. */
    val messageCount: Long? = null,
    /** When the last one went through (RFC 3339). */
    val lastMessageAt: String? = null,
)

/** The result of [ChannelsApi.bind]. */
@Serializable
data class Bound(
    /** Whether the configuration was saved. */
    val saved: Boolean,
    /** Whether the identity was already allowed. */
    val alreadyBound: Boolean,
    /** The peer group it was added to. */
    val group: String,
    /** The channel. */
    val channel: String,
)

/** The result of [ChannelsApi.relink]. */
@Serializable
data class Relinked(
    /** The channel. */
    val channel: String,
    /** `cleared` or `nothing_to_clear`. */
    val outcome: String,
    /** The files removed. */
    val removed: List<String>? = null,
    /** Whether a reload is needed for the channel to pair again. */
    val restartRequired: Boolean? = null,
    /** The gateway's note. */
    val note: String? = null,
)

/**
 * The channel endpoints (`/api/channels`), from [Client.channels]: their
 * state, the identities allowed to talk to them and QR logins.
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/api.rs` and `api_config.rs`
 * (bind). A channel is named `type.alias`, as [list] returns it.
 */
class ChannelsApi internal constructor(
    private val c: Client,
) {
    /** `GET /api/channels`: every configured channel and its state. */
    suspend fun list(): List<Channel> = c.call("GET", listOf("api", "channels"), "channels")

    /**
     * `POST /api/channels/bind`: lets `identity` talk to the channel
     * (Telegram, WeChat and LINE), adding it to the channel's peer group.
     */
    suspend fun bind(
        channelType: String,
        alias: String,
        identity: String,
    ): Bound =
        c.call(
            "POST",
            listOf("api", "channels", "bind"),
            json =
                buildJsonObject {
                    put("channel_type", channelType)
                    put("alias", alias)
                    put("identity", identity)
                },
        )

    /**
     * `POST /api/channels/{channel}/relink`: clears a QR-paired channel's
     * saved login so it pairs again after a reload ([SystemApi.reload]).
     */
    suspend fun relink(channel: String): Relinked = c.call("POST", listOf("api", "channels", channel, "relink"))
}
