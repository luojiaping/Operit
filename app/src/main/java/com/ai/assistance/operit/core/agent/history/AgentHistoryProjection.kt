package com.ai.assistance.operit.core.agent.history

import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.contract.AgentSessionId

enum class AgentHistoryItemKind {
    USER,
    ASSISTANT,
    SUMMARY,
    TOOL_CALL,
    TOOL_RESULT,
    COMPACTION
}

data class AgentHistoryItem(
    val messageId: Long? = null,
    val timestamp: Long,
    val kind: AgentHistoryItemKind,
    val content: String,
    val owner: AgentOwner
)

object AgentHistoryProjection {
    fun forLegacy(items: List<AgentHistoryItem>): List<AgentHistoryItem> {
        return items.filter { item ->
            when (item.owner) {
                AgentOwner.LegacyRoleCard -> true
                AgentOwner.SharedUser -> item.kind == AgentHistoryItemKind.USER

                is AgentOwner.PluginAgent -> false
            }
        }
    }

    fun forPluginAgent(
        items: List<AgentHistoryItem>,
        sessionId: AgentSessionId
    ): List<AgentHistoryItem> {
        return items.filter { item ->
            when (val owner = item.owner) {
                AgentOwner.SharedUser -> item.kind == AgentHistoryItemKind.USER
                AgentOwner.LegacyRoleCard -> false
                is AgentOwner.PluginAgent -> owner.sessionId == sessionId
            }
        }
    }
}
