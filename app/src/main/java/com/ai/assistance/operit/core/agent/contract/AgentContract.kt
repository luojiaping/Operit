package com.ai.assistance.operit.core.agent.contract

enum class AgentMode {
    PLAN,
    BUILD,
    EXPLORE,
    GENERAL
}

enum class AgentStatus {
    IDLE,
    QUEUED,
    RUNNING,
    WAITING_PERMISSION,
    WAITING_CHILD,
    COMPACTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AgentToolCallStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class AgentPermissionEffect {
    ALLOW,
    ASK,
    DENY
}

data class AgentPermissionRule(
    val action: String,
    val resource: String,
    val effect: AgentPermissionEffect
) {
    init {
        require(action.isNotBlank()) { "permission action must not be blank" }
        require(resource.isNotBlank()) { "permission resource must not be blank" }
    }
}

data class AgentProfileDeclaration(
    val pluginId: String,
    val agentId: AgentId,
    val displayName: String,
    val profileVersion: String,
    val mode: AgentMode,
    val promptKey: String,
    val requestedPermissions: List<AgentPermissionRule> = emptyList(),
    val toolIds: List<String> = emptyList(),
    val maxSteps: Int? = null,
    val canSpawnChildren: Boolean = false
) {
    init {
        require(pluginId.isNotBlank()) { "pluginId must not be blank" }
        require(displayName.isNotBlank()) { "agent displayName must not be blank" }
        require(profileVersion.isNotBlank()) { "profileVersion must not be blank" }
        require(promptKey.isNotBlank()) { "promptKey must not be blank" }
        require(maxSteps == null || maxSteps > 0) { "maxSteps must be positive when specified" }
    }
}

data class AgentSessionStart(
    val chatId: String,
    val pluginId: String,
    val agentId: AgentId,
    val displayName: String,
    val profileVersion: String,
    val mode: AgentMode,
    val sessionId: AgentSessionId = AgentSessionId.generate(),
    val parentSessionId: AgentSessionId? = null,
    val depth: Int = 0
) {
    init {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        require(pluginId.isNotBlank()) { "pluginId must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
        require(profileVersion.isNotBlank()) { "profileVersion must not be blank" }
        require(depth >= 0) { "agent depth must not be negative" }
    }
}

data class AgentRunStart(
    val sessionId: AgentSessionId,
    val promptSnapshot: String,
    val modelSnapshotJson: String,
    val permissionSnapshotJson: String,
    val runId: AgentRunId = AgentRunId.generate(),
    val parentRunId: AgentRunId? = null,
    val parentMessageId: Long? = null
)

data class AgentToolCallStart(
    val runId: AgentRunId,
    val sequence: Int,
    val toolName: String,
    val parametersJson: String,
    val callId: AgentToolCallId = AgentToolCallId.generate(),
    val parentCallId: AgentToolCallId? = null
) {
    init {
        require(sequence >= 0) { "tool call sequence must not be negative" }
        require(toolName.isNotBlank()) { "tool name must not be blank" }
    }
}

data class AgentSessionSnapshot(
    val sessionId: AgentSessionId,
    val chatId: String,
    val pluginId: String,
    val agentId: AgentId,
    val displayName: String,
    val profileVersion: String,
    val mode: AgentMode,
    val parentSessionId: AgentSessionId? = null,
    val depth: Int = 0,
    val status: AgentStatus = AgentStatus.IDLE,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long
)

data class AgentRunSnapshot(
    val runId: AgentRunId,
    val sessionId: AgentSessionId,
    val parentRunId: AgentRunId? = null,
    val parentMessageId: Long? = null,
    val promptSnapshot: String,
    val modelSnapshotJson: String,
    val permissionSnapshotJson: String,
    val status: AgentStatus = AgentStatus.QUEUED,
    val summary: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long
)

data class AgentToolCallSnapshot(
    val callId: AgentToolCallId,
    val runId: AgentRunId,
    val parentCallId: AgentToolCallId? = null,
    val sequence: Int,
    val toolName: String,
    val parametersJson: String,
    val status: AgentToolCallStatus = AgentToolCallStatus.QUEUED,
    val resultText: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long
)
