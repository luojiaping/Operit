package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.contract.AgentSessionId

@Entity(
    tableName = "agent_message_owners",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["messageId"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("agentSessionId"),
        Index(value = ["chatId", "agentSessionId"]),
    ],
)
data class AgentMessageOwnerEntity(
    @PrimaryKey val messageId: Long,
    val chatId: String,
    val pluginId: String,
    val agentId: String,
    val agentSessionId: String,
) {
    fun toOwner(): AgentOwner.PluginAgent {
        return AgentOwner.PluginAgent(
            pluginId = pluginId,
            agentId = AgentId(agentId),
            sessionId = AgentSessionId(agentSessionId),
        )
    }
}
