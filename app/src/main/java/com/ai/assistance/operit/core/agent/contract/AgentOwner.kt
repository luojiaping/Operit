package com.ai.assistance.operit.core.agent.contract

sealed interface AgentOwner {
    val key: String

    data object LegacyRoleCard : AgentOwner {
        override val key: String = "legacy"
    }

    data object SharedUser : AgentOwner {
        override val key: String = "shared"
    }

    data class PluginAgent(
        val pluginId: String,
        val agentId: AgentId,
        val sessionId: AgentSessionId
    ) : AgentOwner {
        init {
            require(pluginId.isNotBlank()) { "pluginId must not be blank" }
        }

        override val key: String = "agent:${sessionId.value}"
    }
}
