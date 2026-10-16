package io.github.rustlanglatam.zeroclaw

import kotlinx.serialization.Serializable

/** One model's usage. */
@Serializable
data class ModelStats(
    /** The model. */
    val model: String,
    /** The cost, USD. */
    val costUsd: Double,
    /** The input tokens. */
    val inputTokens: Long,
    /** The cached input tokens. */
    val cachedInputTokens: Long,
    /** The output tokens. */
    val outputTokens: Long,
    /** All tokens. */
    val totalTokens: Long,
    /** The calls. */
    val requestCount: Long,
)

/** One agent's usage. */
@Serializable
data class AgentCostStats(
    /** The agent. */
    val agentAlias: String,
    /** The cost, USD. */
    val costUsd: Double,
    /** All tokens. */
    val totalTokens: Long,
    /** The input tokens. */
    val inputTokens: Long,
    /** The output tokens. */
    val outputTokens: Long,
    /** The cached input tokens. */
    val cachedInputTokens: Long,
    /** The calls. */
    val requestCount: Long,
)

/** Usage totals. */
@Serializable
data class CostSummary(
    /** The cost of the current session, USD. */
    val sessionCostUsd: Double,
    /** The cost today, USD. */
    val dailyCostUsd: Double,
    /** The cost this month, USD. */
    val monthlyCostUsd: Double,
    /** The tokens in the window. */
    val totalTokens: Long,
    /** The model calls in the window. */
    val requestCount: Long,
    /** The usage by model. */
    val byModel: Map<String, ModelStats>? = null,
    /** The usage by agent. */
    val byAgent: Map<String, AgentCostStats>? = null,
)

/** The usage and cost endpoint (`/api/cost`), from [Client.cost]. */
class CostApi internal constructor(
    private val c: Client,
) {
    /**
     * `GET /api/cost`: the usage in `[from, to)`, broken down by model and by
     * agent. Bounds are RFC 3339; an unset one leaves the window open on that
     * side.
     */
    suspend fun summary(
        from: String? = null,
        to: String? = null,
    ): CostSummary = c.call("GET", listOf("api", "cost"), "cost", query = mapOf("from" to from, "to" to to))

    /** `GET /api/cost`: one agent's session and daily figures (the gateway ignores a time window here). */
    suspend fun agent(agent: String): CostSummary = c.call("GET", listOf("api", "cost"), "cost", query = mapOf("agent" to agent))
}
