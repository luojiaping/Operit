package com.ai.assistance.operit.core.agent.kernel

import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentRunStatus
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.contract.AgentStepSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentStepStatus
import com.ai.assistance.operit.core.agent.contract.PersistedAgentMessageRef
import com.ai.assistance.operit.core.agent.contract.ProviderToolCallRef
import com.ai.assistance.operit.core.agent.history.AgentHistoryItem
import com.ai.assistance.operit.core.agent.history.AgentHistoryItemKind
import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelError
import com.ai.assistance.operit.core.agent.model.AgentModelErrorCode
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelRequest
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.core.agent.model.AgentModelUsage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentKernelTest {
    @Test
    fun textOnlyRunCommitsOwnedAssistantMessage() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val client =
            RecordingModelClient { request ->
                flowOf(
                    AgentModelEvent.ReasoningDelta(request.modelRequestId, 0L, "reason"),
                    AgentModelEvent.TextDelta(request.modelRequestId, 1L, "hello "),
                    AgentModelEvent.TextDelta(request.modelRequestId, 2L, "world"),
                    AgentModelEvent.Usage(
                        request.modelRequestId,
                        3L,
                        AgentModelUsage(inputTokens = 12L, cachedInputTokens = 2L, outputTokens = 4L),
                    ),
                    AgentModelEvent.Completed(request.modelRequestId, 4L, AgentModelStopReason.COMPLETE),
                )
            }
        val kernel = AgentKernel(store, client, IncrementingClock())

        val events = mutableListOf<AgentKernelEvent>()
        kernel.execute(command).collect(events::add)

        assertEquals("hello world", store.completedRequest?.assistantText)
        assertEquals("reason", store.completedRequest?.reasoningText)
        assertFalse(store.active)
        assertTrue(events.first() is AgentKernelEvent.RunStarted)
        assertTrue(events[1] is AgentKernelEvent.AssistantReasoningDelta)
        assertTrue(events[2] is AgentKernelEvent.AssistantTextDelta)
        assertTrue(events[3] is AgentKernelEvent.AssistantTextDelta)
        assertTrue(events[4] is AgentKernelEvent.UsageReported)
        assertTrue(events[5] is AgentKernelEvent.AssistantMessageCommitted)
        assertTrue(events[6] is AgentKernelEvent.RunCompleted)
        assertEquals(2, client.lastRequest?.history?.size)
        assertEquals("previous", client.lastRequest?.history?.get(0)?.content)
        assertEquals("question", client.lastRequest?.history?.get(1)?.content)
    }

    @Test
    fun terminalEventStopsProviderFlowImmediately() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val client =
            RecordingModelClient { request ->
                flow {
                    emit(AgentModelEvent.TextDelta(request.modelRequestId, 0L, "done"))
                    emit(AgentModelEvent.Completed(request.modelRequestId, 1L, AgentModelStopReason.COMPLETE))
                    awaitCancellation()
                }
            }

        withTimeout(1_000L) {
            AgentKernel(store, client, IncrementingClock())
                .execute(command)
                .collect { }
        }

        assertFalse(store.active)
        assertEquals("done", store.completedRequest?.assistantText)
    }

    @Test
    fun toolCallFailsWithoutExecutingSideEffects() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val client =
            RecordingModelClient { request ->
                flowOf(
                    AgentModelEvent.ToolCallReady(
                        modelRequestId = request.modelRequestId,
                        eventIndex = 0L,
                        providerCallRef = ProviderToolCallRef("provider-call"),
                        ordinal = 0,
                        toolName = "read_file",
                        argumentsJson = "{}",
                    )
                )
            }
        val events = mutableListOf<AgentKernelEvent>()

        AgentKernel(store, client, IncrementingClock())
            .execute(command)
            .collect(events::add)

        assertEquals("TOOLS_NOT_ENABLED", store.failedRequest?.errorCode)
        assertFalse(store.active)
        assertTrue(events.last() is AgentKernelEvent.RunFailed)
    }

    @Test
    fun providerFailureSettlesRunOnce() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val client =
            RecordingModelClient { request ->
                flowOf(
                    AgentModelEvent.TextDelta(request.modelRequestId, 0L, "partial"),
                    AgentModelEvent.Failed(
                        modelRequestId = request.modelRequestId,
                        eventIndex = 1L,
                        error =
                            AgentModelError(
                                code = AgentModelErrorCode.RATE_LIMIT,
                                message = "rate limited",
                                retryable = true,
                            ),
                    )
                )
            }

        val events = mutableListOf<AgentKernelEvent>()
        AgentKernel(store, client, IncrementingClock())
            .execute(command)
            .collect(events::add)

        assertEquals("MODEL_RATE_LIMIT", store.failedRequest?.errorCode)
        assertEquals("partial", store.failedRequest?.assistantText)
        assertEquals(1, store.failureCount)
        assertTrue(events.last() is AgentKernelEvent.RunFailed)
    }

    @Test
    fun missingTerminalIsProtocolFailure() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val client =
            RecordingModelClient { request ->
                flowOf(AgentModelEvent.TextDelta(request.modelRequestId, 0L, "partial"))
            }

        AgentKernel(store, client, IncrementingClock())
            .execute(command)
            .collect { }

        assertEquals("MODEL_PROTOCOL", store.failedRequest?.errorCode)
        assertFalse(store.active)
    }

    @Test
    fun cancellationReleasesRunLease() = runBlocking {
        val command = command()
        val store = RecordingKernelStore()
        val started = CompletableDeferred<Unit>()
        val client =
            RecordingModelClient {
                flow {
                    emit(AgentModelEvent.TextDelta(command.modelRequestId, 0L, "partial"))
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
        val job = launch {
            AgentKernel(store, client, IncrementingClock())
                .execute(command)
                .collect { }
        }

        started.await()
        job.cancelAndJoin()

        assertEquals(1, store.cancellationCount)
        assertEquals("partial", store.cancelledText)
        assertFalse(store.active)
    }

    @Test
    fun recoveryDelegatesToStore() = runBlocking {
        val store = RecordingKernelStore().apply { interruptedRuns = 2 }
        val client = RecordingModelClient { flowOf() }

        val recovered = AgentKernel(store, client, IncrementingClock()).recoverInterruptedRuns()

        assertEquals(2, recovered)
        assertEquals(0, store.interruptedRuns)
    }

    private fun command(): AgentKernelCommand {
        return AgentKernelCommand(
            sessionId = AgentSessionId("session"),
            userText = "question",
            userTimestamp = 100L,
            promptSnapshot = "system prompt",
            modelSnapshotJson = "{\"provider\":\"fake\"}",
            permissionSnapshotJson = "[]",
            runId = AgentRunId("run"),
            stepId = AgentStepId("step"),
            modelRequestId = AgentModelRequestId("request"),
        )
    }

}

private class RecordingModelClient(
    private val events: (AgentModelRequest) -> Flow<AgentModelEvent>,
) : AgentModelClient {
    var lastRequest: AgentModelRequest? = null

    override fun execute(request: AgentModelRequest): Flow<AgentModelEvent> {
        lastRequest = request
        return events(request)
    }
}

private class RecordingKernelStore : AgentKernelStore {
    var active = false
    var completedRequest: AgentRunCompleteRequest? = null
    var failedRequest: AgentRunFailRequest? = null
    var failureCount = 0
    var cancellationCount = 0
    var cancelledText: String? = null
    var interruptedRuns = 0
    private val reservedRunIds = mutableSetOf<AgentRunId>()
    private var terminalSnapshot: AgentRunTerminalSnapshot? = null

    override suspend fun recoverInterruptedRuns(now: Long): Int {
        val recovered = interruptedRuns
        interruptedRuns = 0
        return recovered
    }

    override suspend fun reserveRun(request: AgentRunReserveRequest): AgentRunReservation {
        check(!active) { "active run already exists" }
        check(reservedRunIds.add(request.command.runId)) { "run ID was already reserved" }
        active = true
        val command = request.command
        return AgentRunReservation(
            session = sessionSnapshot(command.sessionId, AgentSessionStatus.RUNNING),
            run = runSnapshot(command, AgentRunStatus.RUNNING, inputMessageId = 1L),
            step = stepSnapshot(command, AgentStepStatus.RUNNING),
            inputMessage = PersistedAgentMessageRef(1L, "chat", command.userTimestamp),
            history = history(command.sessionId),
        )
    }

    override suspend fun completeRun(request: AgentRunCompleteRequest): AgentRunTerminalSnapshot {
        if (!active) {
            check(completedRequest == request) { "completion payload conflicts with terminal state" }
            return checkNotNull(terminalSnapshot)
        }
        active = false
        completedRequest = request
        return AgentRunTerminalSnapshot(
            session = sessionSnapshot(request.reservation.session.sessionId, AgentSessionStatus.IDLE),
            run =
                request.reservation.run.copy(
                    status = AgentRunStatus.COMPLETED,
                    outputMessageId = 2L,
                ),
            step =
                request.reservation.step.copy(
                    status = AgentStepStatus.COMPLETED,
                    assistantText = request.assistantText,
                    reasoningText = request.reasoningText,
                    usageJson = request.usageJson,
                    finishReason = request.finishReason.name,
                ),
            outputMessage = PersistedAgentMessageRef(2L, "chat", request.assistantTimestamp),
        ).also { snapshot -> terminalSnapshot = snapshot }
    }

    override suspend fun failRun(request: AgentRunFailRequest): AgentRunTerminalSnapshot {
        if (!active) {
            check(failedRequest == request) { "failure payload conflicts with terminal state" }
            return checkNotNull(terminalSnapshot)
        }
        active = false
        failedRequest = request
        failureCount += 1
        return failedTerminal(request.reservation, AgentRunStatus.FAILED, AgentStepStatus.FAILED)
            .also { snapshot -> terminalSnapshot = snapshot }
    }

    override suspend fun cancelRun(
        reservation: AgentRunReservation,
        assistantText: String,
        reasoningText: String,
        usageJson: String?,
        now: Long,
    ): AgentRunTerminalSnapshot {
        if (!active) {
            return checkNotNull(terminalSnapshot)
        }
        active = false
        cancellationCount += 1
        cancelledText = assistantText
        return failedTerminal(reservation, AgentRunStatus.CANCELLED, AgentStepStatus.CANCELLED)
            .also { snapshot -> terminalSnapshot = snapshot }
    }

    private fun failedTerminal(
        reservation: AgentRunReservation,
        runStatus: AgentRunStatus,
        stepStatus: AgentStepStatus,
    ): AgentRunTerminalSnapshot {
        return AgentRunTerminalSnapshot(
            session = sessionSnapshot(reservation.session.sessionId, AgentSessionStatus.IDLE),
            run = reservation.run.copy(status = runStatus),
            step = reservation.step.copy(status = stepStatus),
        )
    }

    private fun sessionSnapshot(
        sessionId: AgentSessionId,
        status: AgentSessionStatus,
    ): AgentSessionSnapshot {
        return AgentSessionSnapshot(
            sessionId = sessionId,
            chatId = "chat",
            pluginId = "plugin",
            agentId = AgentId("agent"),
            displayName = "Agent",
            profileVersion = "1",
            profileKind = AgentProfileKind.PRIMARY,
            modeId = AgentModeId("build"),
            status = status,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    private fun runSnapshot(
        command: AgentKernelCommand,
        status: AgentRunStatus,
        inputMessageId: Long,
    ): AgentRunSnapshot {
        return AgentRunSnapshot(
            runId = command.runId,
            sessionId = command.sessionId,
            parentMessageId = inputMessageId,
            promptSnapshot = command.promptSnapshot,
            modelSnapshotJson = command.modelSnapshotJson,
            permissionSnapshotJson = command.permissionSnapshotJson,
            toolSnapshotJson = command.toolSnapshotJson,
            inputMessageId = inputMessageId,
            status = status,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    private fun stepSnapshot(
        command: AgentKernelCommand,
        status: AgentStepStatus,
    ): AgentStepSnapshot {
        return AgentStepSnapshot(
            stepId = command.stepId,
            runId = command.runId,
            sequence = 0,
            modelRequestId = command.modelRequestId,
            status = status,
            createdAt = 1L,
            startedAt = 1L,
            updatedAt = 1L,
        )
    }

    private fun history(sessionId: AgentSessionId): List<AgentHistoryItem> {
        return listOf(
            AgentHistoryItem(
                messageId = 1L,
                timestamp = 50L,
                kind = AgentHistoryItemKind.ASSISTANT,
                content = "previous",
                owner =
                    AgentOwner.PluginAgent(
                        pluginId = "plugin",
                        agentId = AgentId("agent"),
                        sessionId = sessionId,
                    ),
            ),
            AgentHistoryItem(
                messageId = 2L,
                timestamp = 100L,
                kind = AgentHistoryItemKind.USER,
                content = "question",
                owner = AgentOwner.SharedUser,
            ),
        )
    }
}

private class IncrementingClock : AgentKernelClock {
    private var value = 1_000L

    override fun now(): Long {
        value += 1L
        return value
    }
}
