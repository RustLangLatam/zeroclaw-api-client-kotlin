package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

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

/** The result of [ChannelsApi.redeliver]. */
@Serializable
data class Redelivered(
    /** The channel. */
    val channel: String,
    /** The message. */
    val messageId: String,
    /** `redelivered`. */
    val outcome: String,
)

/** A group chat. */
@Serializable
data class RoomSummary(
    /** Id in the channel's addressing (`…@g.us` on WhatsApp). */
    val id: String,
    /** Name (the WhatsApp group subject). */
    val name: String,
    /** Member count. */
    val participants: Long,
)

/** A member of a group chat. */
@Serializable
data class RoomMember(
    /** The member: `+<digits>` on WhatsApp when the number is known. */
    val id: String,
    /** Whether they administer the group. */
    val admin: Boolean,
)

/** What happened to someone added to a group chat. */
@Serializable
data class Participant(
    /** Who. */
    val user: String,
    /** `added`, `invited`, `refused` or `failed`. */
    val outcome: String,
    /** Why, when it did not work. */
    val detail: String? = null,
)

/** The result of [ChannelsApi.createRoom]. */
@Serializable
data class CreatedRoom(
    /** The channel. */
    val channel: String,
    /** The new room's id. */
    val room: String,
    /** Always `created`. */
    val outcome: String,
    /** What happened to each invitee. */
    val participants: List<Participant> = emptyList(),
)

/**
 * The channel endpoints (`/api/channels`), from [Client.channels]: their
 * state, sending through them, group chats ("rooms") and their members,
 * handing a message to its agent again, the identities allowed to talk to
 * them and QR logins.
 *
 * Gateway handlers: `crates/zeroclaw-gateway/src/api.rs` and `api_config.rs`
 * (bind). A channel is named `type.alias`, as [list] returns it. Sending and
 * managing rooms need pairing on and the channel listed in
 * `[gateway] send_channels`; reading rooms needs pairing only. Errors on these
 * routes carry the channel: `{"channel": …, "error": …}`.
 *
 * Sending, rooms, members and redelivery are routes only the patched
 * `0.8.5+unified.18` build has (declined upstream in
 * zeroclaw-labs/zeroclaw#11434); official ZeroClaw answers them with 404.
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

    /** `POST /api/channels/{channel}/send`: sends plain text to `to`, with no agent turn. */
    suspend fun send(
        channel: String,
        to: String,
        content: String,
        threadId: String? = null,
    ) = c.empty(
        "POST",
        listOf("api", "channels", channel, "send"),
        json =
            buildJsonObject {
                put("to", to)
                put("content", content)
                put("thread_id", threadId)
            },
    )

    /**
     * `POST /api/channels/{channel}/messages/{message_id}/redeliver`: hands a
     * received message to its agent again, unchanged, as a new turn that runs
     * after any turn already running for that sender. The gateway keeps only
     * recent inbound messages, in memory: one it no longer keeps answers 404
     * (`isNotFound`). `messageId` is the one MCP calls carry as
     * `_meta["zeroclaw/message_id"]` (for email, the Message-ID without angle
     * brackets).
     */
    suspend fun redeliver(
        channel: String,
        messageId: String,
    ): Redelivered = c.call("POST", listOf("api", "channels", channel, "messages", messageId, "redeliver"))

    /** `GET /api/channels/{channel}/rooms`: the group chats the running channel takes part in. */
    suspend fun rooms(channel: String): List<RoomSummary> = c.call("GET", listOf("api", "channels", channel, "rooms"), "rooms")

    /**
     * `POST /api/channels/{channel}/rooms`: creates a group chat and invites
     * `invites` (phone numbers on WhatsApp, which are also allowed to talk to
     * the channel).
     */
    suspend fun createRoom(
        channel: String,
        name: String,
        topic: String? = null,
        invites: List<String> = emptyList(),
    ): CreatedRoom =
        c.call(
            "POST",
            listOf("api", "channels", channel, "rooms"),
            json =
                buildJsonObject {
                    put("name", name)
                    put("topic", topic)
                    putJsonArray("invites") { invites.forEach { add(it) } }
                },
        )

    /** `GET /api/channels/{channel}/rooms/{room}/members`: a group chat's members. */
    suspend fun roomMembers(
        channel: String,
        room: String,
    ): List<RoomMember> = c.call("GET", listOf("api", "channels", channel, "rooms", room, "members"), "members")

    /** `POST /api/channels/{channel}/rooms/{room}/invites`: adds or invites `user` to a group chat. */
    suspend fun invite(
        channel: String,
        room: String,
        user: String,
    ): Participant =
        c.call("POST", listOf("api", "channels", channel, "rooms", room, "invites"), json = buildJsonObject { put("user", user) })

    /** `DELETE /api/channels/{channel}/rooms/{room}/members/{user}`: removes a member from a group chat. */
    suspend fun removeMember(
        channel: String,
        room: String,
        user: String,
    ) = c.empty("DELETE", listOf("api", "channels", channel, "rooms", room, "members", user))
}
