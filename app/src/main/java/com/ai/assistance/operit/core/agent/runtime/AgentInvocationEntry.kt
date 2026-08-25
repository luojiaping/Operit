package com.ai.assistance.operit.core.agent.runtime

import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.kernel.AgentKernel
import com.ai.assistance.operit.core.agent.kernel.AgentKernelClock
import com.ai.assistance.operit.core.agent.kernel.AgentKernelCommand
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.kernel.AgentKernelStore
import com.ai.assistance.operit.core.agent.model.AgentModelResolver
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

data class AgentInvocationRequest(
    val chatId: String,
    val userText: String,
    val modelConfigId: String,
    val modelIndex: Int,
    val promptSnapshot: String,
    val permissionSnapshotJson: String,
    val toolSnapshotJson: String = "[]",
    val userTimestamp: Long = ChatMessageTimestampAllocator.next(),
) {
    init {
        require(chatId.isNotBlank()) { "Agent invocation chatId must not be blank" }
        require(userText.isNotBlank()) { "Agent invocation user text must not be blank" }
        require(modelConfigId.isNotBlank()) { "Agent invocation model config ID must not be blank" }
        require(modelIndex >= 0) { "Agent invocation model index must not be negative" }
        require(promptSnapshot.isNotBlank()) { "Agent invocation prompt snapshot must not be blank" }
        require(permissionSnapshotJson.isNotBlank()) {
            "Agent invocation permission snapshot must not be blank"
        }
        require(toolSnapshotJson.isNotBlank()) { "Agent invocation tool snapshot must not be blank" }
        require(userTimestamp > 0L) { "Agent invocation user timestamp must be positive" }
    }
}

class AgentInvocationEntry(
    private val routeResolver: suspend (String) -> AgentRoute,
    private val store: AgentKernelStore,
    private val modelResolver: AgentModelResolver,
    private val startupCoordinator: AgentRuntimeStartupCoordinator,
    private val clock: AgentKernelClock = AgentKernelClock { System.currentTimeMillis() },
) {
    fun execute(request: AgentInvocationRequest): Flow<AgentKernelEvent> =
        flow {
            startupCoordinator.awaitReady()
            val session = requirePluginSession(routeResolver(request.chatId), request.chatId)
            val model = modelResolver.resolve(request.modelConfigId, request.modelIndex)
            val command =
                AgentKernelCommand(
                    sessionId = session.sessionId,
                    userText = request.userText,
                    userTimestamp = request.userTimestamp,
                    promptSnapshot = request.promptSnapshot,
                    modelSnapshotJson = model.modelSnapshotJson,
                    permissionSnapshotJson = request.permissionSnapshotJson,
                    toolSnapshotJson = request.toolSnapshotJson,
                    provider = model.provider,
                    modelName = model.modelName,
                )
            emitAll(AgentKernel(store = store, modelClient = model.client, clock = clock).execute(command))
        }

    private fun requirePluginSession(route: AgentRoute, chatId: String): AgentSessionSnapshot {
        return when (route) {
            is AgentRoute.Plugin -> {
                require(route.chatId == chatId) {
                    "Agent invocation route chat mismatch: ${route.chatId} != $chatId"
                }
                route.session
            }

            is AgentRoute.Legacy ->
                throw AgentInvocationException(
                    "Agent invocation requires an active Agent route: $chatId"
                )
        }
    }

}

class AgentInvocationException(message: String) : IllegalStateException(message)
