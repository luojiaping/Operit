package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunStart
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentStatus

@Entity(
    tableName = "agent_runs",
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("parentRunId"),
        Index("parentMessageId"),
        Index(value = ["sessionId", "updatedAt"]),
    ],
)
data class AgentRunEntity(
    @PrimaryKey val runId: String,
    val sessionId: String,
    val parentRunId: String? = null,
    val parentMessageId: Long? = null,
    val promptSnapshot: String,
    val modelSnapshotJson: String,
    val permissionSnapshotJson: String,
    val status: String = AgentStatus.QUEUED.name,
    val summary: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
) {
    fun toSnapshot(): AgentRunSnapshot {
        return AgentRunSnapshot(
            runId = AgentRunId(runId),
            sessionId = AgentSessionId(sessionId),
            parentRunId = parentRunId?.let(::AgentRunId),
            parentMessageId = parentMessageId,
            promptSnapshot = promptSnapshot,
            modelSnapshotJson = modelSnapshotJson,
            permissionSnapshotJson = permissionSnapshotJson,
            status = AgentStatus.valueOf(status),
            summary = summary,
            errorMessage = errorMessage,
            createdAt = createdAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromStart(input: AgentRunStart, now: Long): AgentRunEntity {
            return AgentRunEntity(
                runId = input.runId.value,
                sessionId = input.sessionId.value,
                parentRunId = input.parentRunId?.value,
                parentMessageId = input.parentMessageId,
                promptSnapshot = input.promptSnapshot,
                modelSnapshotJson = input.modelSnapshotJson,
                permissionSnapshotJson = input.permissionSnapshotJson,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
