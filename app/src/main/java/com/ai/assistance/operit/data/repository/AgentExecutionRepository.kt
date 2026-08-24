package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.core.agent.contract.AgentChatBindingSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentMessageOwnerSnapshot
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
import com.ai.assistance.operit.core.agent.contract.PersistedAgentMessageRef
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import com.ai.assistance.operit.core.agent.routing.AgentRouter
import com.ai.assistance.operit.data.dao.AgentExecutionDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AgentChatBindingEntity
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
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

    private val database = AppDatabase.getDatabase(context)
    private val dao: AgentExecutionDao = database.agentExecutionDao()
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    suspend fun startSession(input: AgentSessionStart, now: Long = System.currentTimeMillis()): AgentSessionSnapshot {
        return database.withTransaction {
            requireNotNull(chatDao.getChatById(input.chatId)) { "Chat not found: ${input.chatId}" }
            when (val parentSessionId = input.parentSessionId) {
                null -> require(input.depth == 0) { "Root Agent session depth must be zero" }
                else -> {
                    val parent = requireNotNull(dao.getSession(parentSessionId.value)) {
                        "Parent Agent session not found: ${parentSessionId.value}"
                    }
                    require(parent.chatId == input.chatId) { "Parent Agent session belongs to another chat" }
                    requireOpenStatus(AgentStatus.valueOf(parent.status), "Parent Agent session")
                    require(input.depth == parent.depth + 1) { "Child Agent session depth must follow its parent" }
                }
            }
            val entity = AgentSessionEntity.fromStart(input, now)
            dao.insertSession(entity)
            entity.toSnapshot()
        }
    }

    suspend fun bindRootSession(
        chatId: String,
        sessionId: AgentSessionId,
        now: Long = System.currentTimeMillis(),
    ): AgentChatBindingSnapshot {
        return database.withTransaction {
            requireNotNull(chatDao.getChatById(chatId)) { "Chat not found: $chatId" }
            val session = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            require(session.chatId == chatId) { "Agent session belongs to another chat" }
            require(session.parentSessionId == null && session.depth == 0) {
                "Only a root Agent session can own a chat route"
            }
            requireOpenStatus(AgentStatus.valueOf(session.status), "Agent session")
            val binding =
                AgentChatBindingEntity(
                    chatId = chatId,
                    activeSessionId = sessionId.value,
                    updatedAt = now,
                )
            dao.setChatBinding(binding)
            binding.toSnapshot()
        }
    }

    suspend fun clearRootSessionBinding(chatId: String) {
        dao.clearChatBinding(chatId)
    }

    suspend fun resolveRoute(chatId: String): AgentRoute {
        return database.withTransaction {
            val binding = dao.getChatBinding(chatId)
            val session =
                when (binding) {
                    null -> null
                    else -> requireNotNull(dao.getSession(binding.activeSessionId)) {
                        "Bound Agent session not found: ${binding.activeSessionId}"
                    }
                }
            AgentRouter.resolve(
                chatId = chatId,
                binding = binding?.toSnapshot(),
                boundSession = session?.toSnapshot(),
            )
        }
    }

    suspend fun updateSessionStatus(
        sessionId: AgentSessionId,
        status: AgentStatus,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): AgentSessionSnapshot {
        return database.withTransaction {
            val current = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            val currentStatus = AgentStatus.valueOf(current.status)
            if (currentStatus == status) {
                if (isTerminalStatus(status)) {
                    dao.clearChatBindingForSession(current.chatId, current.sessionId)
                }
                return@withTransaction current.toSnapshot()
            }
            require(AgentStateTransitions.canTransition(currentStatus, status)) {
                "Invalid Agent session transition: ${currentStatus.name} -> ${status.name}"
            }
            val updated =
                current.copy(
                    status = status.name,
                    startedAt = startedAt ?: current.startedAt,
                    finishedAt = finishedAt ?: current.finishedAt,
                    updatedAt = now,
                )
            dao.updateSession(updated)
            if (isTerminalStatus(status)) {
                dao.clearChatBindingForSession(updated.chatId, updated.sessionId)
            }
            updated.toSnapshot()
        }
    }

    suspend fun startRun(input: AgentRunStart, now: Long = System.currentTimeMillis()): AgentRunSnapshot {
        return database.withTransaction {
            val session = requireNotNull(dao.getSession(input.sessionId.value)) {
                "Agent session not found: ${input.sessionId.value}"
            }
            requireOpenStatus(AgentStatus.valueOf(session.status), "Agent session")
            input.parentRunId?.let { parentRunId ->
                val parentRun = requireNotNull(dao.getRun(parentRunId.value)) {
                    "Parent Agent run not found: ${parentRunId.value}"
                }
                val allowedParentSessionIds =
                    mutableSetOf(session.sessionId).apply {
                        session.parentSessionId?.let { parentSessionId -> add(parentSessionId) }
                    }
                require(parentRun.sessionId in allowedParentSessionIds) {
                    "Parent Agent run is outside the session lineage"
                }
            }
            input.parentMessageId?.let { parentMessageId ->
                val parentMessage = requireNotNull(dao.getMessageIdentity(parentMessageId)) {
                    "Parent message not found: $parentMessageId"
                }
                require(parentMessage.chatId == session.chatId) {
                    "Parent message belongs to another chat"
                }
            }
            val entity = AgentRunEntity.fromStart(input, now)
            dao.insertRun(entity)
            entity.toSnapshot()
        }
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
        return database.withTransaction {
            val current = requireNotNull(dao.getRun(runId.value)) {
                "Agent run not found: ${runId.value}"
            }
            val currentStatus = AgentStatus.valueOf(current.status)
            if (currentStatus == status) {
                return@withTransaction current.toSnapshot()
            }
            require(AgentStateTransitions.canTransition(currentStatus, status)) {
                "Invalid Agent run transition: ${currentStatus.name} -> ${status.name}"
            }
            val updated =
                current.copy(
                    status = status.name,
                    summary = summary ?: current.summary,
                    errorMessage = errorMessage ?: current.errorMessage,
                    startedAt = startedAt ?: current.startedAt,
                    finishedAt = finishedAt ?: current.finishedAt,
                    updatedAt = now,
                )
            dao.updateRun(updated)
            updated.toSnapshot()
        }
    }

    suspend fun beginToolCall(
        input: AgentToolCallStart,
        now: Long = System.currentTimeMillis(),
    ): AgentToolCallSnapshot {
        return database.withTransaction {
            val run = requireNotNull(dao.getRun(input.runId.value)) {
                "Agent run not found: ${input.runId.value}"
            }
            require(AgentStatus.valueOf(run.status) == AgentStatus.RUNNING) {
                "Agent tool calls require a running Agent run"
            }
            require(dao.getToolCallBySequence(input.runId.value, input.sequence) == null) {
                "Agent tool call sequence already exists: ${input.runId.value}/${input.sequence}"
            }
            input.parentCallId?.let { parentCallId ->
                val parentCall = requireNotNull(dao.getToolCall(parentCallId.value)) {
                    "Parent Agent tool call not found: ${parentCallId.value}"
                }
                require(parentCall.runId == input.runId.value) {
                    "Parent Agent tool call belongs to another run"
                }
            }
            val entity = AgentToolCallEntity.fromStart(input, now)
            dao.insertToolCall(entity)
            entity.toSnapshot()
        }
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
        return database.withTransaction {
            val current = requireNotNull(dao.getToolCall(callId.value)) {
                "Agent tool call not found: ${callId.value}"
            }
            val currentStatus = AgentToolCallStatus.valueOf(current.status)
            if (currentStatus == status) {
                return@withTransaction current.toSnapshot()
            }
            require(AgentStateTransitions.canTransition(currentStatus, status)) {
                "Invalid Agent tool call transition: ${currentStatus.name} -> ${status.name}"
            }
            val updated =
                current.copy(
                    status = status.name,
                    resultText = resultText ?: current.resultText,
                    errorMessage = errorMessage ?: current.errorMessage,
                    startedAt = startedAt ?: current.startedAt,
                    finishedAt = finishedAt ?: current.finishedAt,
                    updatedAt = now,
                )
            dao.updateToolCall(updated)
            updated.toSnapshot()
        }
    }

    suspend fun bindMessageToAgent(
        messageId: Long,
        sessionId: AgentSessionId,
    ): AgentMessageOwnerSnapshot {
        return database.withTransaction {
            val message = requireNotNull(dao.getMessageIdentity(messageId)) {
                "Message not found: $messageId"
            }
            requireAgentOwnedSender(message.sender)
            require(message.selectedVariantIndex == 0) {
                "Agent-owned messages do not use Legacy message variants"
            }
            val session = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            requireOpenStatus(AgentStatus.valueOf(session.status), "Agent session")
            require(message.chatId == session.chatId) { "Message and Agent session belong to different chats" }
            val owner = ownerEntity(messageId, session)
            when (val existing = dao.getMessageOwner(messageId)) {
                null -> dao.insertMessageOwner(owner)
                else -> require(existing == owner) { "Message is already owned by another Agent session" }
            }
            requireNotNull(dao.getMessageOwnerRecord(messageId)) {
                "Agent message owner record not found: $messageId"
            }.toSnapshot()
        }
    }

    suspend fun persistAgentMessage(
        sessionId: AgentSessionId,
        message: ChatMessage,
        now: Long = System.currentTimeMillis(),
    ): PersistedAgentMessageRef {
        requireAgentMessageShape(message)
        return database.withTransaction {
            val session = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            requireOpenStatus(AgentStatus.valueOf(session.status), "Agent session")
            val chat = requireNotNull(chatDao.getChatById(session.chatId)) {
                "Chat not found: ${session.chatId}"
            }
            val orderIndex = (messageDao.getMaxOrderIndex(session.chatId) ?: -1) + 1
            val entity =
                MessageEntity.fromChatMessage(
                    chatId = session.chatId,
                    message = message,
                    orderIndex = orderIndex,
                )
            val messageId = messageDao.insertMessage(entity)
            require(messageId > 0L) { "Room did not return a persisted messageId" }
            dao.insertMessageOwner(ownerEntity(messageId, session))
            chatDao.updateChatMetadata(
                chatId = chat.id,
                title = chat.title,
                timestamp = now,
                inputTokens = chat.inputTokens,
                outputTokens = chat.outputTokens,
                currentWindowSize = chat.currentWindowSize,
            )
            PersistedAgentMessageRef(
                messageId = messageId,
                chatId = session.chatId,
                timestamp = message.timestamp,
            )
        }
    }

    suspend fun updateAgentMessage(
        sessionId: AgentSessionId,
        ref: PersistedAgentMessageRef,
        message: ChatMessage,
        now: Long = System.currentTimeMillis(),
    ): PersistedAgentMessageRef {
        requireAgentMessageShape(message)
        require(message.timestamp == ref.timestamp) { "Agent message timestamp must remain stable" }
        return database.withTransaction {
            val session = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            requireOpenStatus(AgentStatus.valueOf(session.status), "Agent session")
            require(session.chatId == ref.chatId) { "Persisted message belongs to another chat" }
            val current = requireNotNull(dao.getMessageIdentity(ref.messageId)) {
                "Message not found: ${ref.messageId}"
            }
            require(current.chatId == ref.chatId) { "Persisted message chat identity changed" }
            val owner = requireNotNull(dao.getMessageOwner(ref.messageId)) {
                "Agent message owner not found: ${ref.messageId}"
            }
            require(owner == ownerEntity(ref.messageId, session)) {
                "Agent message is owned by another session"
            }
            messageDao.updateMessage(
                MessageEntity.fromChatMessage(
                    chatId = ref.chatId,
                    message = message,
                    orderIndex = current.orderIndex,
                    messageId = ref.messageId,
                )
            )
            val chat = requireNotNull(chatDao.getChatById(ref.chatId)) { "Chat not found: ${ref.chatId}" }
            chatDao.updateChatMetadata(
                chatId = chat.id,
                title = chat.title,
                timestamp = now,
                inputTokens = chat.inputTokens,
                outputTokens = chat.outputTokens,
                currentWindowSize = chat.currentWindowSize,
            )
            ref
        }
    }

    suspend fun getSession(sessionId: AgentSessionId): AgentSessionSnapshot? {
        return dao.getSession(sessionId.value)?.toSnapshot()
    }

    suspend fun getMessageOwner(messageId: Long) = dao.getMessageOwnerRecord(messageId)?.toSnapshot()

    suspend fun getChatBinding(chatId: String) = dao.getChatBinding(chatId)?.toSnapshot()

    fun observeSessions(chatId: String): Flow<List<AgentSessionSnapshot>> {
        return dao.observeSessions(chatId).map { entities -> entities.map(AgentSessionEntity::toSnapshot) }
    }

    fun observeChatBinding(chatId: String): Flow<AgentChatBindingSnapshot?> {
        return dao.observeChatBinding(chatId).map { entity -> entity?.toSnapshot() }
    }

    fun observeRuns(sessionId: AgentSessionId): Flow<List<AgentRunSnapshot>> {
        return dao.observeRuns(sessionId.value).map { entities -> entities.map(AgentRunEntity::toSnapshot) }
    }

    fun observeToolCalls(runId: AgentRunId): Flow<List<AgentToolCallSnapshot>> {
        return dao.observeToolCalls(runId.value).map { entities -> entities.map(AgentToolCallEntity::toSnapshot) }
    }

    fun observeMessageOwners(chatId: String) =
        dao.observeMessageOwners(chatId).map { records -> records.map { record -> record.toSnapshot() } }

    private fun ownerEntity(messageId: Long, session: AgentSessionEntity): AgentMessageOwnerEntity {
        return AgentMessageOwnerEntity(
            messageId = messageId,
            chatId = session.chatId,
            agentSessionId = session.sessionId,
        )
    }

    private fun requireAgentOwnedSender(sender: String) {
        require(sender == "ai" || sender == "summary") {
            "Agent-owned messages must use sender 'ai' or 'summary'"
        }
    }

    private fun requireAgentMessageShape(message: ChatMessage) {
        requireAgentOwnedSender(message.sender)
        require(message.selectedVariantIndex == 0 && message.variantCount == 1) {
            "Agent-owned messages do not use Legacy message variants"
        }
    }

    private fun requireOpenStatus(status: AgentStatus, aggregate: String) {
        require(!isTerminalStatus(status)) {
            "$aggregate is terminal: ${status.name}"
        }
    }

    private fun isTerminalStatus(status: AgentStatus): Boolean {
        return status == AgentStatus.COMPLETED ||
            status == AgentStatus.FAILED ||
            status == AgentStatus.CANCELLED
    }
}
