package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.contract.AgentStepSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentStepStart
import com.ai.assistance.operit.core.agent.contract.AgentStepStatus

@Entity(
    tableName = "agent_steps",
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
        Index(value = ["runId", "sequence"], unique = true),
        Index(value = ["modelRequestId"], unique = true),
    ],
)
data class AgentStepEntity(
    @PrimaryKey val stepId: String,
    val runId: String,
    val sequence: Int,
    val modelRequestId: String,
    val status: String = AgentStepStatus.QUEUED.name,
    val assistantText: String? = null,
    val reasoningText: String? = null,
    val usageJson: String? = null,
    val finishReason: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val updatedAt: Long,
) {
    fun toSnapshot(): AgentStepSnapshot {
        return AgentStepSnapshot(
            stepId = AgentStepId(stepId),
            runId = AgentRunId(runId),
            sequence = sequence,
            modelRequestId = AgentModelRequestId(modelRequestId),
            status = AgentStepStatus.valueOf(status),
            assistantText = assistantText,
            reasoningText = reasoningText,
            usageJson = usageJson,
            finishReason = finishReason,
            errorCode = errorCode,
            errorMessage = errorMessage,
            createdAt = createdAt,
            startedAt = startedAt,
            finishedAt = finishedAt,
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromStart(
            input: AgentStepStart,
            now: Long,
            status: AgentStepStatus = AgentStepStatus.QUEUED,
        ): AgentStepEntity {
            return AgentStepEntity(
                stepId = input.stepId.value,
                runId = input.runId.value,
                sequence = input.sequence,
                modelRequestId = input.modelRequestId.value,
                status = status.name,
                createdAt = now,
                startedAt = if (status == AgentStepStatus.RUNNING) now else null,
                updatedAt = now,
            )
        }
    }
}
