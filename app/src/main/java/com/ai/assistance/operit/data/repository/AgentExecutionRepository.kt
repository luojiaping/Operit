package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunStart
import com.ai.assistance.operit.core.agent.contract.AgentStateTransitions
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStart
import com.ai.assistance.operit.core.agent.contract.AgentStatus
import com.ai.assistance.operit.core.agent.contract.AgentToolCallId
import com.ai.assistance.operit.core.agent.contract.AgentToolCallSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStart
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStatus
import com.ai.assistance.operit.data.dao.AgentExecutionDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentExecutionRepository private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: AgentExecutionRepository? = null

        fun getInstance(context: Context): AgentExecutionRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentExecutionRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val dao: AgentExecutionDao = AppDatabase.getDatabase(context).agentExecutionDao()

    suspend fun startSession(input: AgentSessionStart, now: Long = System.currentTimeMillis()): AgentSessionSnapshot {
        val entity = AgentSessionEntity.fromStart(input, now)
        dao.insertSession(entity)
        return entity.toSnapshot()
    }

    suspend fun updateSessionStatus(
        sessionId: AgentSessionId,
        status: AgentStatus,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): AgentSessionSnapshot {
        val current = requireNotNull(dao.getSession(sessionId.value)) {
            "Agent session not found: ${sessionId.value}"
        }
        val currentStatus = AgentStatus.valueOf(current.status)
        require(AgentStateTransitions.canTransition(currentStatus, status)) {
            "Invalid Agent session transition: ${currentStatus.name} -> ${status.name}"
        }
        val updated = current.copy(
            status = status.name,
            startedAt = startedAt ?: current.startedAt,
            finishedAt = finishedAt ?: current.finishedAt,
            updatedAt = now,
        )
        dao.updateSession(updated)
        return updated.toSnapshot()
    }

    suspend fun startRun(input: AgentRunStart, now: Long = System.currentTimeMillis()): AgentRunSnapshot {
        val entity = AgentRunEntity.fromStart(input, now)
        dao.insertRun(entity)
        return entity.toSnapshot()
    }

    suspend fun updateRun(
        runId: AgentRunId,
        status: AgentStatus,
        summary: String? = null,
        errorMessage: String? = null,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): AgentRunSnapshot {
        val current = requireNotNull(dao.getRun(runId.value)) {
            "Agent run not found: ${runId.value}"
        }
        val currentStatus = AgentStatus.valueOf(current.status)
        require(AgentStateTransitions.canTransition(currentStatus, status)) {
            "Invalid Agent run transition: ${currentStatus.name} -> ${status.name}"
        }
        val updated = current.copy(
            status = status.name,
            summary = summary ?: current.summary,
            errorMessage = errorMessage ?: current.errorMessage,
            startedAt = startedAt ?: current.startedAt,
            finishedAt = finishedAt ?: current.finishedAt,
            updatedAt = now,
        )
        dao.updateRun(updated)
        return updated.toSnapshot()
    }

    suspend fun beginToolCall(
        input: AgentToolCallStart,
        now: Long = System.currentTimeMillis(),
    ): AgentToolCallSnapshot {
        val entity = AgentToolCallEntity.fromStart(input, now)
        dao.insertToolCall(entity)
        return entity.toSnapshot()
    }

    suspend fun updateToolCall(
        callId: AgentToolCallId,
        status: AgentToolCallStatus,
        resultText: String? = null,
        errorMessage: String? = null,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): AgentToolCallSnapshot {
        val current = requireNotNull(dao.getToolCall(callId.value)) {
            "Agent tool call not found: ${callId.value}"
        }
        val currentStatus = AgentToolCallStatus.valueOf(current.status)
        require(AgentStateTransitions.canTransition(currentStatus, status)) {
            "Invalid Agent tool call transition: ${currentStatus.name} -> ${status.name}"
        }
        val updated = current.copy(
            status = status.name,
            resultText = resultText ?: current.resultText,
            errorMessage = errorMessage ?: current.errorMessage,
            startedAt = startedAt ?: current.startedAt,
            finishedAt = finishedAt ?: current.finishedAt,
            updatedAt = now,
        )
        dao.updateToolCall(updated)
        return updated.toSnapshot()
    }

    suspend fun recordAgentMessageOwner(
        messageId: Long,
        chatId: String,
        pluginId: String,
        agentId: AgentId,
        sessionId: AgentSessionId,
    ) {
        dao.upsertMessageOwner(
            AgentMessageOwnerEntity(
                messageId = messageId,
                chatId = chatId,
                pluginId = pluginId,
                agentId = agentId.value,
                agentSessionId = sessionId.value,
            )
        )
    }

    suspend fun getSession(sessionId: AgentSessionId): AgentSessionSnapshot? {
        return dao.getSession(sessionId.value)?.toSnapshot()
    }

    suspend fun getMessageOwner(messageId: Long) = dao.getMessageOwner(messageId)?.toOwner()

    fun observeSessions(chatId: String): Flow<List<AgentSessionSnapshot>> {
        return dao.observeSessions(chatId).map { entities -> entities.map(AgentSessionEntity::toSnapshot) }
    }

    fun observeRuns(sessionId: AgentSessionId): Flow<List<AgentRunSnapshot>> {
        return dao.observeRuns(sessionId.value).map { entities -> entities.map(AgentRunEntity::toSnapshot) }
    }

    fun observeToolCalls(runId: AgentRunId): Flow<List<AgentToolCallSnapshot>> {
        return dao.observeToolCalls(runId.value).map { entities -> entities.map(AgentToolCallEntity::toSnapshot) }
    }

    fun observeMessageOwners(chatId: String) =
        dao.observeMessageOwners(chatId).map { entities -> entities.map(AgentMessageOwnerEntity::toOwner) }
}
