package com.ai.assistance.operit.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStart
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus

@Entity(
    tableName = "agent_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("chatId"),
        Index("parentSessionId"),
        Index(value = ["chatId", "updatedAt"]),
        Index(value = ["chatId", "sessionId"], unique = true),
    ],
)
data class AgentSessionEntity(
    @PrimaryKey val sessionId: String,
    val chatId: String,
    val pluginId: String,
    val agentId: String,
    val displayName: String,
    val profileVersion: String,
    @ColumnInfo(defaultValue = "'PRIMARY'") val profileKind: String = AgentProfileKind.PRIMARY.name,
    @ColumnInfo(name = "mode") val modeId: String,
    val parentSessionId: String? = null,
    val depth: Int = 0,
    val status: String = AgentSessionStatus.IDLE.name,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
) {
    fun toSnapshot(): AgentSessionSnapshot {
        return AgentSessionSnapshot(
            sessionId = AgentSessionId(sessionId),
            chatId = chatId,
            pluginId = pluginId,
            agentId = AgentId(agentId),
            displayName = displayName,
            profileVersion = profileVersion,
            profileKind = AgentProfileKind.valueOf(profileKind),
            modeId = AgentModeId(modeId),
            parentSessionId = parentSessionId?.let(::AgentSessionId),
            depth = depth,
            status = AgentSessionStatus.valueOf(status),
            createdAt = createdAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromStart(input: AgentSessionStart, now: Long): AgentSessionEntity {
            return AgentSessionEntity(
                sessionId = input.sessionId.value,
                chatId = input.chatId,
                pluginId = input.pluginId,
                agentId = input.agentId.value,
                displayName = input.displayName,
                profileVersion = input.profileVersion,
                profileKind = input.profileKind.name,
                modeId = input.modeId.value,
                parentSessionId = input.parentSessionId?.value,
                depth = input.depth,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
