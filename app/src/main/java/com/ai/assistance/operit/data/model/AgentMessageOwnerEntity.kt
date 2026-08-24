package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_message_owners",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["chatId", "messageId"],
            childColumns = ["chatId", "messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["chatId", "sessionId"],
            childColumns = ["chatId", "agentSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("agentSessionId"),
        Index(value = ["chatId", "messageId"], unique = true),
        Index(value = ["chatId", "agentSessionId"]),
    ],
)
data class AgentMessageOwnerEntity(
    @PrimaryKey val messageId: Long,
    val chatId: String,
    val agentSessionId: String,
)
