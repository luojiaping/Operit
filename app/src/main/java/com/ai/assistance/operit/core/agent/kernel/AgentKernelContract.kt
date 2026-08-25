package com.ai.assistance.operit.core.agent.kernel

import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.contract.AgentStepSnapshot
import com.ai.assistance.operit.core.agent.contract.PersistedAgentMessageRef
import com.ai.assistance.operit.core.agent.history.AgentHistoryItem
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.core.agent.model.AgentModelUsage

data class AgentKernelCommand(
    val sessionId: AgentSessionId,
    val userText: String,
    val userTimestamp: Long,
    val promptSnapshot: String,
    val modelSnapshotJson: String,
    val permissionSnapshotJson: String,
    val toolSnapshotJson: String = "[]",
    val provider: String = "",
    val modelName: String = "",
    val maxSteps: Int = 1,
    val runId: AgentRunId = AgentRunId.generate(),
    val stepId: AgentStepId = AgentStepId.generate(),
    val modelRequestId: AgentModelRequestId = AgentModelRequestId.generate(),
) {
    init {
        require(userText.isNotBlank()) { "Agent user text must not be blank" }
        require(userTimestamp > 0L) { "Agent user timestamp must be positive" }
        require(promptSnapshot.isNotBlank()) { "Agent prompt snapshot must not be blank" }
        require(modelSnapshotJson.isNotBlank()) { "Agent model snapshot must not be blank" }
        require(permissionSnapshotJson.isNotBlank()) { "Agent permission snapshot must not be blank" }
        require(toolSnapshotJson.isNotBlank()) { "Agent tool snapshot must not be blank" }
        require(maxSteps == 1) { "Text-only AgentKernel supports exactly one step" }
    }
}

data class AgentRunReserveRequest(
    val command: AgentKernelCommand,
    val now: Long,
)

data class AgentRunReservation(
    val session: AgentSessionSnapshot,
    val run: AgentRunSnapshot,
    val step: AgentStepSnapshot,
    val inputMessage: PersistedAgentMessageRef,
    val history: List<AgentHistoryItem>,
    val provider: String = "",
    val modelName: String = "",
)

data class AgentRunCompleteRequest(
    val reservation: AgentRunReservation,
    val assistantText: String,
    val reasoningText: String,
    val usageJson: String?,
    val usage: AgentModelUsage? = null,
    val finishReason: AgentModelStopReason,
    val assistantTimestamp: Long,
    val now: Long,
)

data class AgentRunFailRequest(
    val reservation: AgentRunReservation,
    val errorCode: String,
    val errorMessage: String,
    val assistantText: String,
    val reasoningText: String,
    val usageJson: String?,
    val now: Long,
) {
    init {
        require(errorCode.isNotBlank()) { "Agent run error code must not be blank" }
        require(errorMessage.isNotBlank()) { "Agent run error message must not be blank" }
    }
}

data class AgentRunTerminalSnapshot(
    val session: AgentSessionSnapshot,
    val run: AgentRunSnapshot,
    val step: AgentStepSnapshot,
    val outputMessage: PersistedAgentMessageRef? = null,
)

interface AgentKernelStore {
    suspend fun recoverInterruptedRuns(now: Long): Int

    suspend fun reserveRun(request: AgentRunReserveRequest): AgentRunReservation

    suspend fun completeRun(request: AgentRunCompleteRequest): AgentRunTerminalSnapshot

    suspend fun failRun(request: AgentRunFailRequest): AgentRunTerminalSnapshot

    suspend fun cancelRun(
        reservation: AgentRunReservation,
        assistantText: String,
        reasoningText: String,
        usageJson: String?,
        now: Long,
    ): AgentRunTerminalSnapshot
}

fun interface AgentKernelClock {
    fun now(): Long
}

enum class AgentKernelErrorCode {
    MODEL_FAILED,
    MODEL_PROTOCOL,
    MODEL_STREAM,
    TOOLS_NOT_ENABLED,
    EMPTY_RESPONSE,
}

data class AgentKernelError(
    val code: AgentKernelErrorCode,
    val message: String,
) {
    init {
        require(message.isNotBlank()) { "AgentKernel error message must not be blank" }
    }
}

sealed interface AgentKernelEvent {
    val sessionId: AgentSessionId
    val runId: AgentRunId
    val sequence: Long
    val occurredAt: Long

    data class RunStarted(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val stepId: AgentStepId,
    ) : AgentKernelEvent

    data class AssistantTextDelta(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val text: String,
    ) : AgentKernelEvent

    data class AssistantReasoningDelta(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val text: String,
    ) : AgentKernelEvent

    data class UsageReported(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val usage: AgentModelUsage,
    ) : AgentKernelEvent

    data class AssistantMessageCommitted(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val message: PersistedAgentMessageRef,
    ) : AgentKernelEvent

    data class RunCompleted(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val finishReason: AgentModelStopReason,
    ) : AgentKernelEvent

    data class RunFailed(
        override val sessionId: AgentSessionId,
        override val runId: AgentRunId,
        override val sequence: Long,
        override val occurredAt: Long,
        val error: AgentKernelError,
    ) : AgentKernelEvent
}
