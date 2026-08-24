package com.ai.assistance.operit.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunStart
import com.ai.assistance.operit.core.agent.contract.AgentRunStatus
import com.ai.assistance.operit.core.agent.contract.AgentSessionId

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
        Index("inputMessageId"),
        Index("outputMessageId"),
        Index(value = ["sessionId", "updatedAt"]),
        Index(value = ["sessionId", "runId"], unique = true),
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
    @ColumnInfo(defaultValue = "'[]'") val toolSnapshotJson: String = "[]",
    val inputMessageId: Long? = null,
    val outputMessageId: Long? = null,
    val status: String = AgentRunStatus.QUEUED.name,
    val summary: String? = null,
    val errorCode: String? = null,
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
            toolSnapshotJson = toolSnapshotJson,
            inputMessageId = inputMessageId,
            outputMessageId = outputMessageId,
            status = AgentRunStatus.valueOf(status),
            summary = summary,
            errorCode = errorCode,
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
                toolSnapshotJson = input.toolSnapshotJson,
                inputMessageId = input.inputMessageId,
                createdAt = now,
                updatedAt = now,
            )
        }
    }
}
