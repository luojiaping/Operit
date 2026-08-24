package com.ai.assistance.operit.core.agent.contract

import java.util.UUID

@JvmInline
value class AgentId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentId must not be blank" }
    }
}

@JvmInline
value class AgentModeId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentModeId must not be blank" }
    }
}

@JvmInline
value class AgentSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentSessionId must not be blank" }
    }

    companion object {
        fun generate(): AgentSessionId = AgentSessionId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AgentRunId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentRunId must not be blank" }
    }

    companion object {
        fun generate(): AgentRunId = AgentRunId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AgentToolCallId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentToolCallId must not be blank" }
    }

    companion object {
        fun generate(): AgentToolCallId = AgentToolCallId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AgentStepId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentStepId must not be blank" }
    }

    companion object {
        fun generate(): AgentStepId = AgentStepId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class AgentModelRequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "agentModelRequestId must not be blank" }
    }

    companion object {
        fun generate(): AgentModelRequestId = AgentModelRequestId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class ProviderToolCallRef(val value: String) {
    init {
        require(value.isNotBlank()) { "providerToolCallRef must not be blank" }
    }
}
