package com.ai.assistance.operit.core.agent.model

import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.contract.ProviderToolCallRef
import kotlinx.coroutines.flow.Flow

enum class AgentModelMessageRole {
    USER,
    ASSISTANT,
}

data class AgentModelMessage(
    val role: AgentModelMessageRole,
    val content: String,
)

data class AgentModelRequest(
    val modelRequestId: AgentModelRequestId,
    val sessionId: AgentSessionId,
    val runId: AgentRunId,
    val stepId: AgentStepId,
    val modelSnapshotJson: String,
    val systemPrompt: String,
    val history: List<AgentModelMessage>,
    val toolSnapshotJson: String,
)

data class AgentModelUsage(
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val outputTokens: Long,
) {
    init {
        require(inputTokens >= 0L) { "inputTokens must not be negative" }
        require(cachedInputTokens >= 0L) { "cachedInputTokens must not be negative" }
        require(outputTokens >= 0L) { "outputTokens must not be negative" }
        require(cachedInputTokens <= inputTokens) { "cachedInputTokens must not exceed inputTokens" }
    }
}

enum class AgentModelStopReason {
    COMPLETE,
    LENGTH,
    TOOL_CALL,
}

enum class AgentModelErrorCode {
    AUTHENTICATION,
    RATE_LIMIT,
    INVALID_REQUEST,
    NETWORK,
    PROVIDER,
    PROTOCOL,
}

data class AgentModelError(
    val code: AgentModelErrorCode,
    val message: String,
    val retryable: Boolean,
) {
    init {
        require(message.isNotBlank()) { "Agent model error message must not be blank" }
    }
}

sealed interface AgentModelEvent {
    val modelRequestId: AgentModelRequestId
    val eventIndex: Long

    data class TextDelta(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val text: String,
    ) : AgentModelEvent

    data class ReasoningDelta(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val text: String,
    ) : AgentModelEvent

    data class ToolCallReady(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val providerCallRef: ProviderToolCallRef,
        val ordinal: Int,
        val toolName: String,
        val argumentsJson: String,
    ) : AgentModelEvent {
        init {
            require(ordinal >= 0) { "Provider tool call ordinal must not be negative" }
            require(toolName.isNotBlank()) { "Provider tool call name must not be blank" }
            require(argumentsJson.isNotBlank()) { "Provider tool call arguments must not be blank" }
        }
    }

    data class Usage(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val usage: AgentModelUsage,
    ) : AgentModelEvent

    data class Completed(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val stopReason: AgentModelStopReason,
    ) : AgentModelEvent

    data class Failed(
        override val modelRequestId: AgentModelRequestId,
        override val eventIndex: Long,
        val error: AgentModelError,
    ) : AgentModelEvent
}

interface AgentModelClient {
    fun execute(request: AgentModelRequest): Flow<AgentModelEvent>
}

data class AgentModelResolution(
    val modelSnapshotJson: String,
    val client: AgentModelClient,
) {
    init {
        require(modelSnapshotJson.isNotBlank()) { "Agent model snapshot must not be blank" }
    }
}

fun interface AgentModelResolver {
    suspend fun resolve(modelConfigId: String, modelIndex: Int): AgentModelResolution
}
