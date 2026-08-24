package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunLeaseSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionId

@Entity(
    tableName = "agent_run_leases",
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AgentRunEntity::class,
            parentColumns = ["sessionId", "runId"],
            childColumns = ["sessionId", "runId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId", "runId"], unique = true),
        Index(value = ["runId"], unique = true),
    ],
)
data class AgentRunLeaseEntity(
    @PrimaryKey val sessionId: String,
    val runId: String,
    val acquiredAt: Long,
) {
    fun toSnapshot(): AgentRunLeaseSnapshot {
        return AgentRunLeaseSnapshot(
            sessionId = AgentSessionId(sessionId),
            runId = AgentRunId(runId),
            acquiredAt = acquiredAt,
        )
    }
}
