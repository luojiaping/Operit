package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.api.chat.llmprovider.agent.OpenAiResponsesAgentInvocationEntryFactory
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
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

class AgentChatTurnCoordinator(
    private val coroutineScope: CoroutineScope,
    private val chatHistoryDelegate: ChatHistoryDelegate,
    private val messageProcessingDelegate: MessageProcessingDelegate,
    private val repository: AgentExecutionRepository,
    private val invocationEntryProvider: () -> AgentInvocationEntry,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val invocationEntry by lazy(invocationEntryProvider)

    suspend fun resolveRoute(chatId: String): AgentRoute {
        return repository.resolveRoute(chatId)
    }

    suspend fun activateAgentForChat(chatId: String): AgentSessionSnapshot {
        require(chatId.isNotBlank()) { "Agent activation chatId must not be blank" }
        return when (val route = repository.resolveRoute(chatId)) {
            is AgentRoute.Plugin -> route.session
            is AgentRoute.Legacy -> {
                val session =
                    repository.startSession(
                        input =
                            com.ai.assistance.operit.core.agent.contract.AgentSessionStart(
                                chatId = chatId,
                                pluginId = BUILTIN_PLUGIN_ID,
                                agentId = AgentId(BUILTIN_AGENT_ID),
                                displayName = BUILTIN_DISPLAY_NAME,
                                profileVersion = BUILTIN_PROFILE_VERSION,
                                profileKind = AgentProfileKind.PRIMARY,
                                modeId = AgentModeId(BUILTIN_MODE_ID),
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

    fun start(request: AgentInvocationRequest): Boolean {
        synchronized(jobs) {
            if (jobs[request.chatId]?.isActive == true ||
                messageProcessingDelegate.isChatLoading(request.chatId)
            ) {
                return false
            }
            val job = coroutineScope.launch(Dispatchers.IO) { execute(request) }
            jobs[request.chatId] = job
            job.invokeOnCompletion { jobs.remove(request.chatId, job) }
            return true
        }
    }

    fun cancel(chatId: String) {
        jobs[chatId]?.cancel()
    }

    fun isActive(chatId: String): Boolean = jobs[chatId]?.isActive == true

    private suspend fun execute(request: AgentInvocationRequest) {
        if (!messageProcessingDelegate.beginExternalAgentTurn(request.chatId)) {
            AppLogger.w(TAG, "Agent chat turn rejected because chat is already processing: ${request.chatId}")
            return
        }
        val previewTimestamp = ChatMessageTimestampAllocator.next()
        val text = StringBuilder()
        var settled = false
        try {
            invocationEntry.execute(request).collect { event ->
                when (event) {
                    is AgentKernelEvent.AssistantTextDelta -> {
                        text.append(event.text)
                        chatHistoryDelegate.addMessageToChat(
                            ChatMessage(
                                sender = "ai",
                                content = text.toString(),
                                timestamp = previewTimestamp,
                                roleName = BUILTIN_DISPLAY_NAME,
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
                            state = InputProcessingState.Completed,
                        )
                    }

                    is AgentKernelEvent.RunFailed -> {
                        settled = true
                        messageProcessingDelegate.finishExternalAgentTurn(
                            chatId = request.chatId,
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
                    state = InputProcessingState.Error("Agent event stream ended without settlement"),
                )
                chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
            }
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                messageProcessingDelegate.finishExternalAgentTurn(
                    chatId = request.chatId,
                    state = InputProcessingState.Idle,
                )
                chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
            }
            throw cancellation
        } catch (error: Throwable) {
            AppLogger.e(TAG, "Agent chat turn failed", error)
            messageProcessingDelegate.finishExternalAgentTurn(
                chatId = request.chatId,
                state = InputProcessingState.Error(error.message ?: "Agent chat turn failed"),
            )
            chatHistoryDelegate.reloadChatMessagesSmart(request.chatId)
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

        const val TEXT_ONLY_SYSTEM_PROMPT =
            "You are a text-only Agent runtime. Do not call tools, emit XML, or explain your process."
        private const val BUILTIN_PLUGIN_ID = "operit.agent"
        private const val BUILTIN_AGENT_ID = "operit.primary"
        private const val BUILTIN_DISPLAY_NAME = "Operit Agent"
        private const val BUILTIN_PROFILE_VERSION = "1"
        private const val BUILTIN_MODE_ID = "text"
        private const val TAG = "AgentChatTurnCoordinator"
    }
}
