package com.ai.assistance.operit.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentRunStatus
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.contract.AgentStepStatus
import com.ai.assistance.operit.core.agent.kernel.AgentKernel
import com.ai.assistance.operit.core.agent.kernel.AgentKernelClock
import com.ai.assistance.operit.core.agent.kernel.AgentKernelCommand
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelError
import com.ai.assistance.operit.core.agent.model.AgentModelErrorCode
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelRequest
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentExecutionRepositoryAndroidTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AgentExecutionRepository

    @Before
    fun setUp() = runBlocking {
        database =
            Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getInstrumentation().targetContext,
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
        repository = AgentExecutionRepository(database, AgentHistoryRepository(database))
        database.chatDao().insertChat(
            ChatEntity(
                id = CHAT_ID,
                title = "Agent test",
                createdAt = 1L,
                updatedAt = 1L,
                displayOrder = 1L,
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completedKernelRunPersistsMessagesOwnersAndLeaseSettlement() = runBlocking {
        val sessionId = createBoundRootSession()
        val events = mutableListOf<AgentKernelEvent>()

        AgentKernel(
            store = repository,
            modelClient = FixtureModelClient { request ->
                flowOf(
                    AgentModelEvent.TextDelta(request.modelRequestId, 0L, "answer"),
                    AgentModelEvent.Completed(
                        request.modelRequestId,
                        1L,
                        AgentModelStopReason.COMPLETE,
                    ),
                )
            },
            clock = FixedClock(),
        ).execute(command(sessionId, "complete")).collect(events::add)

        val dao = database.agentExecutionDao()
        val run = requireNotNull(dao.getRun("complete-run"))
        val step = requireNotNull(dao.getStep("complete-step"))
        val outputMessageId = requireNotNull(run.outputMessageId)
        val owner = requireNotNull(dao.getMessageOwnerRecord(outputMessageId))

        assertEquals(AgentRunStatus.COMPLETED.name, run.status)
        assertEquals(AgentStepStatus.COMPLETED.name, step.status)
        assertEquals("answer", step.assistantText)
        assertEquals("plugin", owner.pluginId)
        assertEquals(sessionId.value, owner.agentSessionId)
        assertNull(dao.getRunLease(sessionId.value))
        assertTrue(events.any { event -> event is AgentKernelEvent.RunCompleted })
    }

    @Test
    fun failedKernelRunPersistsPartialPayloadAndError() = runBlocking {
        val sessionId = createBoundRootSession()

        AgentKernel(
            store = repository,
            modelClient = FixtureModelClient { request ->
                flowOf(
                    AgentModelEvent.TextDelta(request.modelRequestId, 0L, "partial"),
                    AgentModelEvent.Failed(
                        request.modelRequestId,
                        1L,
                        AgentModelError(
                            code = AgentModelErrorCode.PROVIDER,
                            message = "provider failed",
                            retryable = false,
                        ),
                    ),
                )
            },
            clock = FixedClock(),
        ).execute(command(sessionId, "failed")).collect { }

        val dao = database.agentExecutionDao()
        val run = requireNotNull(dao.getRun("failed-run"))
        val step = requireNotNull(dao.getStep("failed-step"))

        assertEquals(AgentRunStatus.FAILED.name, run.status)
        assertEquals("MODEL_PROVIDER", run.errorCode)
        assertEquals(AgentStepStatus.FAILED.name, step.status)
        assertEquals("partial", step.assistantText)
        assertEquals("MODEL_PROVIDER", step.errorCode)
        assertNull(dao.getRunLease(sessionId.value))
    }

    @Test
    fun cancellationPersistsPartialPayloadAndReleasesLease() = runBlocking {
        val sessionId = createBoundRootSession()
        val modelStarted = CompletableDeferred<Unit>()
        val job =
            launch {
                AgentKernel(
                    store = repository,
                    modelClient = FixtureModelClient { request ->
                        flow {
                            emit(AgentModelEvent.TextDelta(request.modelRequestId, 0L, "partial"))
                            modelStarted.complete(Unit)
                            awaitCancellation()
                        }
                    },
                    clock = FixedClock(),
                ).execute(command(sessionId, "cancel")).collect { }
            }

        modelStarted.await()
        withTimeout(5_000L) {
            job.cancelAndJoin()
        }

        val dao = database.agentExecutionDao()
        val run = requireNotNull(dao.getRun("cancel-run"))
        val step = requireNotNull(dao.getStep("cancel-step"))

        assertEquals(AgentRunStatus.CANCELLED.name, run.status)
        assertEquals("CANCELLED", run.errorCode)
        assertEquals(AgentStepStatus.CANCELLED.name, step.status)
        assertEquals("partial", step.assistantText)
        assertNull(dao.getRunLease(sessionId.value))
    }

    @Test
    fun recoveryMarksLeasedRunInterruptedAndReturnsSessionToIdle() = runBlocking {
        val sessionId = createBoundRootSession()
        repository.reserveRun(
            com.ai.assistance.operit.core.agent.kernel.AgentRunReserveRequest(
                command = command(sessionId, "recovery"),
                now = 20L,
            )
        )

        assertEquals(1, repository.recoverInterruptedRuns(30L))

        val dao = database.agentExecutionDao()
        val session = requireNotNull(dao.getSession(sessionId.value))
        val run = requireNotNull(dao.getRun("recovery-run"))
        val step = requireNotNull(dao.getStep("recovery-step"))

        assertEquals(AgentSessionStatus.IDLE.name, session.status)
        assertEquals(AgentRunStatus.FAILED.name, run.status)
        assertEquals("PROCESS_INTERRUPTED", run.errorCode)
        assertEquals(AgentStepStatus.FAILED.name, step.status)
        assertEquals("PROCESS_INTERRUPTED", step.errorCode)
        assertNull(dao.getRunLease(sessionId.value))
    }

    private suspend fun createBoundRootSession(): AgentSessionId {
        val session =
            repository.startSession(
                com.ai.assistance.operit.core.agent.contract.AgentSessionStart(
                    chatId = CHAT_ID,
                    pluginId = "plugin",
                    agentId = AgentId("agent"),
                    displayName = "Agent",
                    profileVersion = "1",
                    profileKind = AgentProfileKind.PRIMARY,
                    modeId = AgentModeId("build"),
                    sessionId = AgentSessionId("session-${System.nanoTime()}"),
                ),
                now = 10L,
            )
        repository.bindRootSession(CHAT_ID, session.sessionId, now = 10L)
        return session.sessionId
    }

    private fun command(sessionId: AgentSessionId, suffix: String): AgentKernelCommand {
        return AgentKernelCommand(
            sessionId = sessionId,
            userText = "question",
            userTimestamp = 100L,
            promptSnapshot = "system prompt",
            modelSnapshotJson = "{\"model\":\"test\"}",
            permissionSnapshotJson = "[]",
            runId = AgentRunId("$suffix-run"),
            stepId = AgentStepId("$suffix-step"),
            modelRequestId = AgentModelRequestId("$suffix-request"),
        )
    }

    private class FixtureModelClient(
        private val events: (AgentModelRequest) -> Flow<AgentModelEvent>,
    ) : AgentModelClient {
        override fun execute(request: AgentModelRequest): Flow<AgentModelEvent> = events(request)
    }

    private class FixedClock : AgentKernelClock {
        override fun now(): Long = 1_000L
    }

    private companion object {
        const val CHAT_ID = "agent-test-chat"
    }
}
