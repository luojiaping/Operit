package com.ai.assistance.operit.core.agent.runtime

import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.kernel.IncrementingClock
import com.ai.assistance.operit.core.agent.kernel.RecordingKernelStore
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelResolution
import com.ai.assistance.operit.core.agent.model.AgentModelResolver
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentInvocationEntryTest {
    @Test
    fun pluginRouteResolvesModelAndExecutesKernel() = runBlocking {
        val startupCoordinator = readyCoordinator(this)
        val store = RecordingKernelStore()
        val modelClient = RecordingInvocationModelClient()
        var resolvedConfigId: String? = null
        var resolvedModelIndex: Int? = null
        val modelResolver =
            AgentModelResolver { configId, modelIndex ->
                resolvedConfigId = configId
                resolvedModelIndex = modelIndex
                AgentModelResolution(
                    modelSnapshotJson = "{\"model\":\"snapshot\"}",
                    client = modelClient,
                )
            }
        val entry =
            AgentInvocationEntry(
                routeResolver = { chatId -> AgentRoute.Plugin(chatId, sessionSnapshot()) },
                store = store,
                modelResolver = modelResolver,
                startupCoordinator = startupCoordinator,
                clock = IncrementingClock(),
            )
        val events = mutableListOf<AgentKernelEvent>()

        entry.execute(request()).collect(events::add)

        assertEquals("config", resolvedConfigId)
        assertEquals(2, resolvedModelIndex)
        assertEquals("answer", store.completedRequest?.assistantText)
        assertTrue(events.any { event -> event is AgentKernelEvent.RunCompleted })
    }

    @Test
    fun legacyRouteIsRejectedBeforeModelResolution() = runBlocking {
        val startupCoordinator = readyCoordinator(this)
        var resolverCalled = false
        val entry =
            AgentInvocationEntry(
                routeResolver = { chatId -> AgentRoute.Legacy(chatId) },
                store = RecordingKernelStore(),
                modelResolver =
                    AgentModelResolver { _, _ ->
                        resolverCalled = true
                        error("model resolver must not run for Legacy route")
                    },
                startupCoordinator = startupCoordinator,
            )

        try {
            entry.execute(request()).collect { }
            error("Legacy route should have been rejected")
        } catch (exception: AgentInvocationException) {
            assertEquals(
                "Agent invocation requires an active Agent route: chat",
                exception.message,
            )
        }

        assertTrue(!resolverCalled)
    }

    private fun readyCoordinator(scope: CoroutineScope): AgentRuntimeStartupCoordinator {
        return AgentRuntimeStartupCoordinator { 0 }.also { coordinator ->
            coordinator.start(scope)
        }
    }

    private fun request(): AgentInvocationRequest {
        return AgentInvocationRequest(
            chatId = "chat",
            userText = "question",
            modelConfigId = "config",
            modelIndex = 2,
            promptSnapshot = "system prompt",
            permissionSnapshotJson = "[]",
            userTimestamp = 100L,
        )
    }

    private fun sessionSnapshot(): AgentSessionSnapshot {
        return AgentSessionSnapshot(
            sessionId = AgentSessionId("session"),
            chatId = "chat",
            pluginId = "plugin",
            agentId = AgentId("agent"),
            displayName = "Agent",
            profileVersion = "1",
            profileKind = AgentProfileKind.PRIMARY,
            modeId = AgentModeId("build"),
            status = AgentSessionStatus.IDLE,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}

private class RecordingInvocationModelClient : AgentModelClient {
    override fun execute(request: com.ai.assistance.operit.core.agent.model.AgentModelRequest): Flow<AgentModelEvent> {
        return flowOf(
            AgentModelEvent.TextDelta(
                modelRequestId = request.modelRequestId,
                eventIndex = 0L,
                text = "answer",
            ),
            AgentModelEvent.Completed(
                modelRequestId = request.modelRequestId,
                eventIndex = 1L,
                stopReason = AgentModelStopReason.COMPLETE,
            ),
        )
    }
}
