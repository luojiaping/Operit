package com.ai.assistance.operit.core.agent.history

import com.ai.assistance.operit.core.agent.contract.AgentOwner

data class AgentHistorySourceMessage(
    val messageId: Long,
    val timestamp: Long,
    val orderIndex: Int,
    val sender: String,
    val content: String,
    val pluginOwner: AgentOwner.PluginAgent?,
)

object AgentHistoryAdapter {
    fun adapt(messages: List<AgentHistorySourceMessage>): List<AgentHistoryItem> {
        return messages
            .sortedWith(compareBy(AgentHistorySourceMessage::orderIndex, AgentHistorySourceMessage::messageId))
            .map(::adaptMessage)
    }

    private fun adaptMessage(message: AgentHistorySourceMessage): AgentHistoryItem {
        require(message.messageId > 0L) { "messageId must be positive" }
        val kind =
            when (message.sender) {
                "user" -> AgentHistoryItemKind.USER
                "ai" -> AgentHistoryItemKind.ASSISTANT
                "summary" -> AgentHistoryItemKind.SUMMARY
                else -> throw IllegalArgumentException("Unsupported history sender: ${message.sender}")
            }
        val owner =
            when (message.sender) {
                "user" -> {
                    require(message.pluginOwner == null) { "User messages must use shared ownership" }
                    AgentOwner.SharedUser
                }

                "ai", "summary" ->
                    when (val pluginOwner = message.pluginOwner) {
                        null -> AgentOwner.LegacyRoleCard
                        else -> pluginOwner
                    }

                else -> error("Sender was validated before owner mapping")
            }
        return AgentHistoryItem(
            messageId = message.messageId,
            timestamp = message.timestamp,
            kind = kind,
            content = message.content,
            owner = owner,
        )
    }
}
