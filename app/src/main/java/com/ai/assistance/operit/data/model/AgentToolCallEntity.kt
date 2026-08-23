package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentToolCallId
import com.ai.assistance.operit.core.agent.contract.AgentToolCallSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStart
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStatus

@Entity(
    tableName = "agent_tool_calls",
    foreignKeys = [
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["runId"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("runId"),
        Index("parentCallId"),
        Index(value = ["runId", "sequence"]),
    ],
)
data class AgentToolCallEntity(
    @PrimaryKey val callId: String,
    val runId: String,
    val parentCallId: String? = null,
    val sequence: Int,
    val toolName: String,
    val parametersJson: String,
    val status: String = AgentToolCallStatus.QUEUED.name,
    val resultText: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
) {
    fun toSnapshot(): AgentToolCallSnapshot {
        return AgentToolCallSnapshot(
            callId = AgentToolCallId(callId),
            runId = AgentRunId(runId),
            parentCallId = parentCallId?.let(::AgentToolCallId),
            sequence = sequence,
            toolName = toolName,
            parametersJson = parametersJson,
            status = AgentToolCallStatus.valueOf(status),
            resultText = resultText,
            errorMessage = errorMessage,
            startedAt = startedAt,
            finishedAt = finishedAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromStart(input: AgentToolCallStart, now: Long): AgentToolCallEntity {
            return AgentToolCallEntity(
                callId = input.callId.value,
                runId = input.runId.value,
                parentCallId = input.parentCallId?.value,
                sequence = input.sequence,
                toolName = input.toolName,
                parametersJson = input.parametersJson,
                updatedAt = now,
            )
        }
    }
}
