package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationEntry
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationRequest
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.repository.AgentExecutionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AgentChatTurnCoordinatorTest {
    @Test
    fun activationCreatesAndBindsPrimaryRootSession() = runBlocking {
        val repository = mock<AgentExecutionRepository>()
        val history = mock<ChatHistoryDelegate>()
        val processing = mock<MessageProcessingDelegate>()
        val entry = mock<AgentInvocationEntry>()
        val session = sessionSnapshot()
        doReturn(AgentRoute.Legacy("chat")).whenever(repository).resolveRoute("chat")
        doReturn(session).whenever(repository).startSession(any(), any())
        val coordinator = coordinator(repository, history, processing, entry)

        val activated = coordinator.activateAgentForChat("chat")

        assertEquals(session, activated)
        val bindInvocation =
            mockingDetails(repository).invocations.single { invocation ->
                invocation.method.name.startsWith("bindRootSession")
            }
        assertEquals("chat", bindInvocation.arguments[0])
        assertEquals(session.sessionId.value, bindInvocation.arguments[1])
        Unit
    }

    @Test
    fun completedEventsPublishTransientPreviewAndReloadPersistedHistory() = runBlocking {
        val repository = mock<AgentExecutionRepository>()
        val history = mock<ChatHistoryDelegate>()
        val processing = mock<MessageProcessingDelegate>()
        val entry = mock<AgentInvocationEntry>()
        whenever(processing.beginExternalAgentTurn(eq("chat"), any())).thenReturn(1L)
        whenever(
            entry.execute(any())
        ).thenReturn(
            flowOf(
                AgentKernelEvent.RunStarted(
                    sessionId = AgentSessionId("session"),
                    runId = com.ai.assistance.operit.core.agent.contract.AgentRunId("run"),
                    sequence = 0L,
                    occurredAt = 1L,
                    stepId = com.ai.assistance.operit.core.agent.contract.AgentStepId("step"),
                ),
                AgentKernelEvent.AssistantTextDelta(
                    sessionId = AgentSessionId("session"),
                    runId = com.ai.assistance.operit.core.agent.contract.AgentRunId("run"),
                    sequence = 1L,
                    occurredAt = 2L,
                    text = "answer",
                ),
                AgentKernelEvent.AssistantMessageCommitted(
                    sessionId = AgentSessionId("session"),
                    runId = com.ai.assistance.operit.core.agent.contract.AgentRunId("run"),
                    sequence = 2L,
                    occurredAt = 3L,
                    message = com.ai.assistance.operit.core.agent.contract.PersistedAgentMessageRef(
                        messageId = 2L,
                        chatId = "chat",
                        timestamp = 4L,
                    ),
                ),
                AgentKernelEvent.RunCompleted(
                    sessionId = AgentSessionId("session"),
                    runId = com.ai.assistance.operit.core.agent.contract.AgentRunId("run"),
                    sequence = 3L,
                    occurredAt = 4L,
                    finishReason = com.ai.assistance.operit.core.agent.model.AgentModelStopReason.COMPLETE,
                ),
            )
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val coordinator = coordinator(repository, history, processing, entry, scope)

        try {
            assertTrue(coordinator.start(request()))
            withTimeout(5_000L) {
                while (coordinator.isActive("chat")) {
                    delay(10L)
                }
            }

            verify(history).addMessageToChat(
                argThat { message ->
                    message.isVariantPreview && message.sender == "ai" && message.content == "answer"
                },
                eq("chat"),
            )
            verify(history).reloadChatMessagesSmart("chat")
            verify(processing).finishExternalAgentTurn("chat", 1L, InputProcessingState.Completed)
        } finally {
            scope.cancel()
        }
        Unit
    }

    private fun coordinator(
        repository: AgentExecutionRepository,
        history: ChatHistoryDelegate,
        processing: MessageProcessingDelegate,
        entry: AgentInvocationEntry,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    ): AgentChatTurnCoordinator {
        return AgentChatTurnCoordinator(
            coroutineScope = scope,
            chatHistoryDelegate = history,
            messageProcessingDelegate = processing,
            repository = repository,
            invocationEntryProvider = { entry },
        )
    }

    private fun request(): AgentInvocationRequest {
        return AgentInvocationRequest(
            chatId = "chat",
            userText = "question",
            modelConfigId = "config",
            modelIndex = 0,
            promptSnapshot = "system prompt",
            permissionSnapshotJson = "[]",
        )
    }

    private fun sessionSnapshot(): AgentSessionSnapshot {
        return AgentSessionSnapshot(
            sessionId = AgentSessionId("session"),
            chatId = "chat",
            pluginId = "operit.agent",
            agentId = AgentId("operit.primary"),
            displayName = "Operit Agent",
            profileVersion = "1",
            profileKind = AgentProfileKind.PRIMARY,
            modeId = AgentModeId("text"),
            status = AgentSessionStatus.IDLE,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
