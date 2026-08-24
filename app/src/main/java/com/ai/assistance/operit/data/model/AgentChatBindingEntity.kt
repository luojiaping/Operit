package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentChatBindingSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionId

@Entity(
    tableName = "agent_chat_bindings",
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["chatId", "sessionId"],
            childColumns = ["chatId", "activeSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chatId", "activeSessionId"], unique = true),
        Index(value = ["activeSessionId"], unique = true),
    ],
)
data class AgentChatBindingEntity(
    @PrimaryKey val chatId: String,
    val activeSessionId: String,
    val updatedAt: Long,
) {
    fun toSnapshot(): AgentChatBindingSnapshot {
        return AgentChatBindingSnapshot(
            chatId = chatId,
            activeSessionId = AgentSessionId(activeSessionId),
            updatedAt = updatedAt,
        )
    }
}
