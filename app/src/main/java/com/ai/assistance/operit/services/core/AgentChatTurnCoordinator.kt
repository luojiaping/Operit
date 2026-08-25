package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.agent.OpenAiResponsesAgentInvocationEntryFactory
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.registry.AgentPluginRegistry
import com.ai.assistance.operit.core.agent.registry.BuiltinTextAgentPlugin
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.routing.AgentRoute
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationEntry
import com.ai.assistance.operit.core.agent.runtime.AgentInvocationRequest
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.repository.AgentExecutionRepository
import com.ai.assistance.operit.util.AppLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.util.stream.MutableSharedStreamImpl

class AgentChatTurnCoordinator(
    private val coroutineScope: CoroutineScope,
    private val chatHistoryDelegate: ChatHistoryDelegate,
    private val messageProcessingDelegate: MessageProcessingDelegate,
    private val repository: AgentExecutionRepository,
    private val invocationEntryProvider: () -> AgentInvocationEntry,
    private val pluginRegistry: AgentPluginRegistry = AgentPluginRegistry.global,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val activeResponseStreams = ConcurrentHashMap<String, MutableSharedStreamImpl<String>>()
    private val activeTurnIds = ConcurrentHashMap<String, Long>()
    private val invocationEntry by lazy(invocationEntryProvider)

    suspend fun resolveRoute(chatId: String): AgentRoute {
        return repository.resolveRoute(chatId)
    }

    suspend fun activateAgentForChat(chatId: String): AgentSessionSnapshot {
        require(chatId.isNotBlank()) { "Agent activation chatId must not be blank" }
        return when (val route = repository.resolveRoute(chatId)) {
            is AgentRoute.Plugin -> route.session
            is AgentRoute.Legacy -> {
                val registration =
                    pluginRegistry.requireEnabled(
                        pluginId = BuiltinTextAgentPlugin.PLUGIN_ID,
                        agentId = BuiltinTextAgentPlugin.AGENT_ID,
                        profileVersion = BuiltinTextAgentPlugin.PROFILE_VERSION,
                        modeId = BuiltinTextAgentPlugin.MODE_ID,
                    )
                val declaration = registration.declaration
                val session =
                    repository.getLatestOpenRootSession(chatId)
                        ?.takeIf { existing ->
                            existing.pluginId == declaration.pluginId &&
                                existing.agentId == declaration.agentId &&
                                existing.profileVersion == declaration.profileVersion &&
                                existing.modeId == declaration.modeId
                        }
                        ?: repository.startSession(
                            input =
                                com.ai.assistance.operit.core.agent.contract.AgentSessionStart(
                                    chatId = chatId,
                                    pluginId = declaration.pluginId,
                                    agentId = declaration.agentId,
                                    displayName = declaration.displayName,
                                    profileVersion = declaration.profileVersion,
                                    profileKind = declaration.profileKind,
                                    modeId = declaration.modeId,
                                    sessionId = AgentSessionId.generate(),
                                )
                        )
                repository.bindRootSession(chatId = chatId, sessionId = session.sessionId)
                session
            }
        }
    }

    suspend fun deactivateAgentForChat(chatId: String) {
        cancel(chatId)
        repository.clearRootSessionBinding(chatId)
    }

    suspend fun buildInvocationRequest(
        chatId: String,
        userText: String,
        modelConfigId: String,
        modelIndex: Int,
    ): AgentInvocationRequest {
        val route = repository.resolveRoute(chatId)
        val pluginRoute = requireNotNull(route as? AgentRoute.Plugin) {
            "Agent invocation requires an active Agent route: $chatId"
        }
        val registration =
            pluginRegistry.requireEnabled(
                pluginId = pluginRoute.session.pluginId,
                agentId = pluginRoute.session.agentId,
                profileVersion = pluginRoute.session.profileVersion,
                modeId = pluginRoute.session.modeId,
            )
        return AgentInvocationRequest(
            chatId = chatId,
            userText = userText,
            modelConfigId = modelConfigId,
            modelIndex = modelIndex,
            promptSnapshot = registration.promptSnapshot,
            permissionSnapshotJson = registration.permissionSnapshotJson,
            toolSnapshotJson = registration.toolSnapshotJson,
        )
    }

    fun start(request: AgentInvocationRequest): Boolean {
        synchronized(jobs) {
            if (jobs[request.chatId]?.isActive == true ||
                messageProcessingDelegate.isChatLoading(request.chatId)
            ) {
                return false
            }
            val responseStream = MutableSharedStreamImpl<String>()
            val turnId =
                messageProcessingDelegate.beginExternalAgentTurn(
                    request.chatId,
                    responseStream,
                ) ?: run {
                    responseStream.close()
                    return false
                }
            activeResponseStreams[request.chatId] = responseStream
            activeTurnIds[request.chatId] = turnId
            val job = coroutineScope.launch(Dispatchers.IO) { execute(request) }
            jobs[request.chatId] = job
            job.invokeOnCompletion {
                jobs.remove(request.chatId, job)
                activeResponseStreams.remove(request.chatId, responseStream)
                activeTurnIds.remove(request.chatId, turnId)
                if (!job.isCompleted || job.isCancelled) {
                    responseStream.close()
                    messageProcessingDelegate.finishExternalAgentTurn(
                        chatId = request.chatId,
                        turnId = turnId,
                        state = InputProcessingState.Idle,
                    )
                }
            }
            return true
        }
    }

    fun cancel(chatId: String) {
        jobs[chatId]?.cancel()
    }

    fun isActive(chatId: String): Boolean = jobs[chatId]?.isActive == true

    private suspend fun execute(request: AgentInvocationRequest) {
        val responseStream = requireNotNull(activeResponseStreams[request.chatId])
        val turnId = requireNotNull(activeTurnIds[request.chatId])
        val previewTimestamp = ChatMessageTimestampAllocator.next()
        val text = StringBuilder()
        var settled = false
        try {
            invocationEntry.execute(request).collect { event ->
                when (event) {
                    is AgentKernelEvent.AssistantTextDelta -> {
                        text.append(event.text)
                        responseStream.emit(event.text)
                        chatHistoryDelegate.addMessageToChat(
                            ChatMessage(
                                sender = "ai",
                                content = text.toString(),
                                timestamp = previewTimestamp,
                                roleName = BuiltinTextAgentPlugin.DISPLAY_NAME,
                                isVariantPreview = true,
                            ),
                            chatIdOverride = request.chatId,
                        )
                        messageProcessingDelegate.requestExternalScrollToBottom(request.chatId)
                    }

                    is AgentKernelEvent.AssistantMessageCommitted -> {
                        chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
                    }

                    is AgentKernelEvent.RunCompleted -> {
                        settled = true
                        messageProcessingDelegate.finishExternalAgentTurn(
                            chatId = request.chatId,
                            turnId = turnId,
                            state = InputProcessingState.Completed,
                        )
                    }

                    is AgentKernelEvent.RunFailed -> {
                        settled = true
                        messageProcessingDelegate.finishExternalAgentTurn(
                            chatId = request.chatId,
                            turnId = turnId,
                            state = InputProcessingState.Error(event.error.message),
                        )
                        chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
                    }

                    is AgentKernelEvent.AssistantReasoningDelta,
                    is AgentKernelEvent.RunStarted,
                    is AgentKernelEvent.UsageReported -> Unit
                }
            }
            if (!settled) {
                messageProcessingDelegate.finishExternalAgentTurn(
                    chatId = request.chatId,
                    turnId = turnId,
                    state = InputProcessingState.Error("Agent event stream ended without settlement"),
                )
                chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                messageProcessingDelegate.finishExternalAgentTurn(
                    chatId = request.chatId,
                    turnId = turnId,
                    state = InputProcessingState.Idle,
                )
                chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
            }
            throw cancellation
        } catch (error: Throwable) {
            AppLogger.e(TAG, "Agent chat turn failed", error)
            messageProcessingDelegate.finishExternalAgentTurn(
                chatId = request.chatId,
                turnId = turnId,
                state = InputProcessingState.Error(error.message ?: "Agent chat turn failed"),
            )
            chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
        } finally {
            responseStream.close()
            activeResponseStreams.remove(request.chatId, responseStream)
            activeTurnIds.remove(request.chatId, turnId)
        }
    }

    companion object {
        fun create(
            context: Context,
            coroutineScope: CoroutineScope,
            chatHistoryDelegate: ChatHistoryDelegate,
            messageProcessingDelegate: MessageProcessingDelegate,
        ): AgentChatTurnCoordinator {
            return AgentChatTurnCoordinator(
                coroutineScope = coroutineScope,
                chatHistoryDelegate = chatHistoryDelegate,
                messageProcessingDelegate = messageProcessingDelegate,
                repository = AgentExecutionRepository.getInstance(context),
                invocationEntryProvider = {
                    OpenAiResponsesAgentInvocationEntryFactory.create(context)
                },
            )
        }

        private const val TAG = "AgentChatTurnCoordinator"
    }
}
