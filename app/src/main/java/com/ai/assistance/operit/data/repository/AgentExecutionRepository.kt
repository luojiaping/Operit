package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.core.agent.contract.AgentChatBindingSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentMessageOwnerSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunStart
import com.ai.assistance.operit.core.agent.contract.AgentRunStatus
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.contract.AgentStateTransitions
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStart
import com.ai.assistance.operit.core.agent.contract.AgentStepSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentStepStart
import com.ai.assistance.operit.core.agent.contract.AgentStepStatus
import com.ai.assistance.operit.core.agent.contract.AgentToolCallId
import com.ai.assistance.operit.core.agent.contract.AgentToolCallSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStart
import com.ai.assistance.operit.core.agent.contract.AgentToolCallStatus
import com.ai.assistance.operit.core.agent.contract.PersistedAgentMessageRef
import com.ai.assistance.operit.core.agent.kernel.AgentKernelStore
import com.ai.assistance.operit.core.agent.kernel.AgentRunCompleteRequest
import com.ai.assistance.operit.core.agent.kernel.AgentRunFailRequest
import com.ai.assistance.operit.core.agent.kernel.AgentRunReservation
import com.ai.assistance.operit.core.agent.kernel.AgentRunReserveRequest
import com.ai.assistance.operit.core.agent.kernel.AgentRunTerminalSnapshot
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import com.ai.assistance.operit.core.agent.routing.AgentRouter
import com.ai.assistance.operit.data.dao.AgentExecutionDao
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.AgentChatBindingEntity
import com.ai.assistance.operit.data.model.AgentMessageOwnerEntity
import com.ai.assistance.operit.data.model.AgentRunEntity
import com.ai.assistance.operit.data.model.AgentRunLeaseEntity
import com.ai.assistance.operit.data.model.AgentSessionEntity
import com.ai.assistance.operit.data.model.AgentStepEntity
import com.ai.assistance.operit.data.model.AgentToolCallEntity
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AgentExecutionRepository internal constructor(
    private val database: AppDatabase,
    private val historyRepository: AgentHistoryRepository,
) : AgentKernelStore {
    companion object {
        @Volatile
        private var instance: AgentExecutionRepository? = null

        fun getInstance(context: Context): AgentExecutionRepository {
            return instance ?: synchronized(this) {
                instance ?: AgentExecutionRepository(
                    database = AppDatabase.getDatabase(context.applicationContext),
                    historyRepository = AgentHistoryRepository.getInstance(context.applicationContext),
                ).also { instance = it }
            }
        }
    }

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
                    requireOpenSessionStatus(AgentSessionStatus.valueOf(parent.status), "Parent Agent session")
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
            requireOpenSessionStatus(AgentSessionStatus.valueOf(session.status), "Agent session")
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
        status: AgentSessionStatus,
        startedAt: Long? = null,
        finishedAt: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): AgentSessionSnapshot {
        return database.withTransaction {
            val current = requireNotNull(dao.getSession(sessionId.value)) {
                "Agent session not found: ${sessionId.value}"
            }
            require(dao.getRunLease(current.sessionId) == null) {
                "Agent session status is owned by an active run lease"
            }
            val currentStatus = AgentSessionStatus.valueOf(current.status)
            if (currentStatus == status) {
                if (isTerminalSessionStatus(status)) {
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
            if (isTerminalSessionStatus(status)) {
                dao.clearChatBindingForSession(updated.chatId, updated.sessionId)
            }
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
            require(AgentRunStatus.valueOf(run.status) == AgentRunStatus.RUNNING) {
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
            requireOpenSessionStatus(AgentSessionStatus.valueOf(session.status), "Agent session")
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
            requireOpenSessionStatus(AgentSessionStatus.valueOf(session.status), "Agent session")
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
            requireOpenSessionStatus(AgentSessionStatus.valueOf(session.status), "Agent session")
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

    override suspend fun recoverInterruptedRuns(now: Long): Int {
        return database.withTransaction {
            val leases = dao.getRunLeases()
            leases.forEach { lease ->
                val session = requireNotNull(dao.getSession(lease.sessionId)) {
                    "Leased Agent session not found: ${lease.sessionId}"
                }
                val run = requireNotNull(dao.getRun(lease.runId)) {
                    "Leased Agent run not found: ${lease.runId}"
                }
                val step = requireNotNull(dao.getRunningStep(lease.runId)) {
                    "Running Agent step not found: ${lease.runId}"
                }
                val errorCode = "PROCESS_INTERRUPTED"
                val errorMessage = "Agent process ended before run settlement"
                dao.updateStep(
                    step.copy(
                        status = AgentStepStatus.FAILED.name,
                        errorCode = errorCode,
                        errorMessage = errorMessage,
                        finishedAt = now,
                        updatedAt = now,
                    )
                )
                dao.updateRun(
                    run.copy(
                        status = AgentRunStatus.FAILED.name,
                        errorCode = errorCode,
                        errorMessage = errorMessage,
                        finishedAt = now,
                        updatedAt = now,
                    )
                )
                dao.deleteRunLease(lease.sessionId, lease.runId)
                dao.updateSession(
                    session.copy(
                        status = AgentSessionStatus.IDLE.name,
                        updatedAt = now,
                    )
                )
            }
            leases.size
        }
    }

    override suspend fun reserveRun(request: AgentRunReserveRequest): AgentRunReservation {
        val command = request.command
        return database.withTransaction {
            val session = requireNotNull(dao.getSession(command.sessionId.value)) {
                "Agent session not found: ${command.sessionId.value}"
            }
            require(session.parentSessionId == null && session.depth == 0) {
                "Text-only AgentKernel requires a root Agent session"
            }
            require(AgentSessionStatus.valueOf(session.status) == AgentSessionStatus.IDLE) {
                "Agent session is not idle: ${session.status}"
            }
            require(dao.getRunLease(session.sessionId) == null) {
                "Agent session already has an active run"
            }
            val binding = requireNotNull(dao.getChatBinding(session.chatId)) {
                "Agent chat binding not found: ${session.chatId}"
            }
            require(binding.activeSessionId == session.sessionId) {
                "Agent session is not the active root route"
            }
            val chat = requireNotNull(chatDao.getChatById(session.chatId)) {
                "Chat not found: ${session.chatId}"
            }
            val inputMessage =
                persistSharedUserMessageLocked(
                    chat = chat,
                    content = command.userText,
                    timestamp = command.userTimestamp,
                    now = request.now,
                )
            val run =
                AgentRunEntity.fromStart(
                    input =
                        AgentRunStart(
                            sessionId = command.sessionId,
                            promptSnapshot = command.promptSnapshot,
                            modelSnapshotJson = command.modelSnapshotJson,
                            permissionSnapshotJson = command.permissionSnapshotJson,
                            toolSnapshotJson = command.toolSnapshotJson,
                            runId = command.runId,
                            parentMessageId = inputMessage.messageId,
                            inputMessageId = inputMessage.messageId,
                        ),
                    now = request.now,
                ).copy(
                    status = AgentRunStatus.RUNNING.name,
                    startedAt = request.now,
                )
            dao.insertRun(run)
            val step =
                AgentStepEntity.fromStart(
                    input =
                        AgentStepStart(
                            runId = command.runId,
                            sequence = 0,
                            modelRequestId = command.modelRequestId,
                            stepId = command.stepId,
                        ),
                    now = request.now,
                    status = AgentStepStatus.RUNNING,
                )
            dao.insertStep(step)
            dao.insertRunLease(
                AgentRunLeaseEntity(
                    sessionId = session.sessionId,
                    runId = run.runId,
                    acquiredAt = request.now,
                )
            )
            val sessionStartedAt =
                when (val startedAt = session.startedAt) {
                    null -> request.now
                    else -> startedAt
                }
            val runningSession =
                session.copy(
                    status = AgentSessionStatus.RUNNING.name,
                    startedAt = sessionStartedAt,
                    updatedAt = request.now,
                )
            dao.updateSession(runningSession)
            val history =
                historyRepository.loadPluginHistory(
                    chatId = runningSession.chatId,
                    sessionId = command.sessionId,
                )
            AgentRunReservation(
                session = runningSession.toSnapshot(),
                run = run.toSnapshot(),
                step = step.toSnapshot(),
                inputMessage = inputMessage,
                history = history,
            )
        }
    }

    override suspend fun completeRun(request: AgentRunCompleteRequest): AgentRunTerminalSnapshot {
        require(request.assistantText.isNotBlank()) { "Agent assistant text must not be blank" }
        require(request.assistantTimestamp > 0L) { "Agent assistant timestamp must be positive" }
        return database.withTransaction {
            val current = loadReservationStateLocked(request.reservation)
            if (AgentRunStatus.valueOf(current.run.status) == AgentRunStatus.COMPLETED) {
                requireCompletedPayloadMatches(current.step, request)
                val outputMessageId = requireNotNull(current.run.outputMessageId) {
                    "Completed Agent run has no output message"
                }
                val outputIdentity = requireNotNull(dao.getMessageIdentity(outputMessageId)) {
                    "Completed Agent output message not found: $outputMessageId"
                }
                require(outputIdentity.chatId == current.session.chatId) {
                    "Completed Agent output belongs to another chat"
                }
                require(outputIdentity.sender == "ai") {
                    "Completed Agent output has an invalid sender"
                }
                require(outputIdentity.timestamp == request.assistantTimestamp) {
                    "Agent completion timestamp conflicts with the persisted value"
                }
                require(dao.getMessageOwner(outputMessageId) == ownerEntity(outputMessageId, current.session)) {
                    "Completed Agent output owner conflicts with the persisted value"
                }
                return@withTransaction AgentRunTerminalSnapshot(
                    session = current.session.toSnapshot(),
                    run = current.run.toSnapshot(),
                    step = current.step.toSnapshot(),
                    outputMessage =
                        PersistedAgentMessageRef(
                            messageId = outputIdentity.messageId,
                            chatId = outputIdentity.chatId,
                            timestamp = outputIdentity.timestamp,
                        ),
                )
            }
            requireActiveReservationLocked(current, request.reservation)
            val chat = requireNotNull(chatDao.getChatById(current.session.chatId)) {
                "Chat not found: ${current.session.chatId}"
            }
            val outputMessage =
                persistAgentOutputLocked(
                    session = current.session,
                    chat = chat,
                    content = request.assistantText,
                    timestamp = request.assistantTimestamp,
                    now = request.now,
                )
            val completedStep =
                current.step.copy(
                    status = AgentStepStatus.COMPLETED.name,
                    assistantText = request.assistantText,
                    reasoningText = request.reasoningText,
                    usageJson = request.usageJson,
                    finishReason = request.finishReason.name,
                    errorCode = null,
                    errorMessage = null,
                    finishedAt = request.now,
                    updatedAt = request.now,
                )
            dao.updateStep(completedStep)
            val completedRun =
                current.run.copy(
                    outputMessageId = outputMessage.messageId,
                    status = AgentRunStatus.COMPLETED.name,
                    errorCode = null,
                    errorMessage = null,
                    finishedAt = request.now,
                    updatedAt = request.now,
                )
            dao.updateRun(completedRun)
            dao.deleteRunLease(current.session.sessionId, current.run.runId)
            val idleSession =
                current.session.copy(
                    status = AgentSessionStatus.IDLE.name,
                    updatedAt = request.now,
                )
            dao.updateSession(idleSession)
            AgentRunTerminalSnapshot(
                session = idleSession.toSnapshot(),
                run = completedRun.toSnapshot(),
                step = completedStep.toSnapshot(),
                outputMessage = outputMessage,
            )
        }
    }

    override suspend fun failRun(request: AgentRunFailRequest): AgentRunTerminalSnapshot {
        return settleRunFailure(
            request = request,
            runStatus = AgentRunStatus.FAILED,
            stepStatus = AgentStepStatus.FAILED,
        )
    }

    override suspend fun cancelRun(
        reservation: AgentRunReservation,
        assistantText: String,
        reasoningText: String,
        usageJson: String?,
        now: Long,
    ): AgentRunTerminalSnapshot {
        return settleRunFailure(
            request =
                AgentRunFailRequest(
                    reservation = reservation,
                    errorCode = "CANCELLED",
                    errorMessage = "Agent run cancelled",
                    assistantText = assistantText,
                    reasoningText = reasoningText,
                    usageJson = usageJson,
                    now = now,
                ),
            runStatus = AgentRunStatus.CANCELLED,
            stepStatus = AgentStepStatus.CANCELLED,
        )
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

    fun observeSteps(runId: AgentRunId): Flow<List<AgentStepSnapshot>> {
        return dao.observeSteps(runId.value).map { entities -> entities.map(AgentStepEntity::toSnapshot) }
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

    private suspend fun settleRunFailure(
        request: AgentRunFailRequest,
        runStatus: AgentRunStatus,
        stepStatus: AgentStepStatus,
    ): AgentRunTerminalSnapshot {
        return database.withTransaction {
            val current = loadReservationStateLocked(request.reservation)
            val currentRunStatus = AgentRunStatus.valueOf(current.run.status)
            if (currentRunStatus == runStatus) {
                require(current.run.errorCode == request.errorCode) {
                    "Agent run terminal error code conflicts with the persisted value"
                }
                require(current.run.errorMessage == request.errorMessage) {
                    "Agent run terminal error message conflicts with the persisted value"
                }
                require(current.step.status == stepStatus.name) {
                    "Agent step terminal status conflicts with the persisted value"
                }
                require(current.step.errorCode == request.errorCode) {
                    "Agent step terminal error code conflicts with the persisted value"
                }
                require(current.step.errorMessage == request.errorMessage) {
                    "Agent step terminal error message conflicts with the persisted value"
                }
                require(current.step.assistantText == request.assistantText) {
                    "Agent step partial text conflicts with the persisted value"
                }
                require(current.step.reasoningText == request.reasoningText) {
                    "Agent step partial reasoning conflicts with the persisted value"
                }
                require(current.step.usageJson == request.usageJson) {
                    "Agent step partial usage conflicts with the persisted value"
                }
                return@withTransaction AgentRunTerminalSnapshot(
                    session = current.session.toSnapshot(),
                    run = current.run.toSnapshot(),
                    step = current.step.toSnapshot(),
                )
            }
            requireActiveReservationLocked(current, request.reservation)
            val settledStep =
                current.step.copy(
                    status = stepStatus.name,
                    assistantText = request.assistantText,
                    reasoningText = request.reasoningText,
                    usageJson = request.usageJson,
                    errorCode = request.errorCode,
                    errorMessage = request.errorMessage,
                    finishedAt = request.now,
                    updatedAt = request.now,
                )
            dao.updateStep(settledStep)
            val settledRun =
                current.run.copy(
                    status = runStatus.name,
                    errorCode = request.errorCode,
                    errorMessage = request.errorMessage,
                    finishedAt = request.now,
                    updatedAt = request.now,
                )
            dao.updateRun(settledRun)
            dao.deleteRunLease(current.session.sessionId, current.run.runId)
            val idleSession =
                current.session.copy(
                    status = AgentSessionStatus.IDLE.name,
                    updatedAt = request.now,
                )
            dao.updateSession(idleSession)
            AgentRunTerminalSnapshot(
                session = idleSession.toSnapshot(),
                run = settledRun.toSnapshot(),
                step = settledStep.toSnapshot(),
            )
        }
    }

    private suspend fun loadReservationStateLocked(
        reservation: AgentRunReservation,
    ): ReservationState {
        val session = requireNotNull(dao.getSession(reservation.session.sessionId.value)) {
            "Agent session not found: ${reservation.session.sessionId.value}"
        }
        val run = requireNotNull(dao.getRun(reservation.run.runId.value)) {
            "Agent run not found: ${reservation.run.runId.value}"
        }
        val step = requireNotNull(dao.getStep(reservation.step.stepId.value)) {
            "Agent step not found: ${reservation.step.stepId.value}"
        }
        require(run.sessionId == session.sessionId) { "Agent run belongs to another session" }
        require(step.runId == run.runId) { "Agent step belongs to another run" }
        require(reservation.run.runId.value == run.runId) { "Agent reservation run identity changed" }
        require(reservation.step.stepId.value == step.stepId) { "Agent reservation step identity changed" }
        return ReservationState(session = session, run = run, step = step)
    }

    private suspend fun requireActiveReservationLocked(
        current: ReservationState,
        reservation: AgentRunReservation,
    ) {
        require(current.run.sessionId == current.session.sessionId) {
            "Agent run belongs to another session"
        }
        require(current.step.runId == current.run.runId) {
            "Agent step belongs to another run"
        }
        require(AgentSessionStatus.valueOf(current.session.status) == AgentSessionStatus.RUNNING) {
            "Agent session is not running"
        }
        require(AgentRunStatus.valueOf(current.run.status) == AgentRunStatus.RUNNING) {
            "Agent run is not running"
        }
        require(AgentStepStatus.valueOf(current.step.status) == AgentStepStatus.RUNNING) {
            "Agent step is not running"
        }
        val lease = requireNotNull(dao.getRunLease(current.session.sessionId)) {
            "Agent run lease not found: ${current.session.sessionId}"
        }
        require(lease.runId == current.run.runId) { "Agent run lease points to another run" }
        require(reservation.run.runId.value == current.run.runId) { "Agent reservation run identity changed" }
        require(reservation.step.stepId.value == current.step.stepId) { "Agent reservation step identity changed" }
    }

    private fun requireCompletedPayloadMatches(
        step: AgentStepEntity,
        request: AgentRunCompleteRequest,
    ) {
        require(step.status == AgentStepStatus.COMPLETED.name) {
            "Completed Agent run has a non-completed step"
        }
        require(step.assistantText == request.assistantText) {
            "Agent completion text conflicts with the persisted value"
        }
        require(step.reasoningText == request.reasoningText) {
            "Agent completion reasoning conflicts with the persisted value"
        }
        require(step.usageJson == request.usageJson) {
            "Agent completion usage conflicts with the persisted value"
        }
        require(step.finishReason == request.finishReason.name) {
            "Agent completion finish reason conflicts with the persisted value"
        }
    }

    private suspend fun persistSharedUserMessageLocked(
        chat: ChatEntity,
        content: String,
        timestamp: Long,
        now: Long,
    ): PersistedAgentMessageRef {
        val orderIndex = (messageDao.getMaxOrderIndex(chat.id) ?: -1) + 1
        val message =
            ChatMessage(
                sender = "user",
                content = content,
                timestamp = timestamp,
            )
        val messageId =
            messageDao.insertMessage(
                MessageEntity.fromChatMessage(
                    chatId = chat.id,
                    message = message,
                    orderIndex = orderIndex,
                )
            )
        require(messageId > 0L) { "Room did not return a persisted user messageId" }
        updateChatMetadataLocked(chat, now)
        return PersistedAgentMessageRef(messageId = messageId, chatId = chat.id, timestamp = timestamp)
    }

    private suspend fun persistAgentOutputLocked(
        session: AgentSessionEntity,
        chat: ChatEntity,
        content: String,
        timestamp: Long,
        now: Long,
    ): PersistedAgentMessageRef {
        val orderIndex = (messageDao.getMaxOrderIndex(chat.id) ?: -1) + 1
        val message =
            ChatMessage(
                sender = "ai",
                content = content,
                timestamp = timestamp,
            )
        val messageId =
            messageDao.insertMessage(
                MessageEntity.fromChatMessage(
                    chatId = chat.id,
                    message = message,
                    orderIndex = orderIndex,
                )
            )
        require(messageId > 0L) { "Room did not return a persisted Agent output messageId" }
        dao.insertMessageOwner(ownerEntity(messageId, session))
        updateChatMetadataLocked(chat, now)
        return PersistedAgentMessageRef(messageId = messageId, chatId = chat.id, timestamp = timestamp)
    }

    private suspend fun updateChatMetadataLocked(
        chat: ChatEntity,
        now: Long,
    ) {
        chatDao.updateChatMetadata(
            chatId = chat.id,
            title = chat.title,
            timestamp = now,
            inputTokens = chat.inputTokens,
            outputTokens = chat.outputTokens,
            currentWindowSize = chat.currentWindowSize,
        )
    }

    private data class ReservationState(
        val session: AgentSessionEntity,
        val run: AgentRunEntity,
        val step: AgentStepEntity,
    )

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

    private fun requireOpenSessionStatus(status: AgentSessionStatus, aggregate: String) {
        require(!isTerminalSessionStatus(status)) {
            "$aggregate is terminal: ${status.name}"
        }
    }

    private fun isTerminalSessionStatus(status: AgentSessionStatus): Boolean {
        return status == AgentSessionStatus.COMPLETED ||
            status == AgentSessionStatus.FAILED ||
            status == AgentSessionStatus.CANCELLED
    }
}
