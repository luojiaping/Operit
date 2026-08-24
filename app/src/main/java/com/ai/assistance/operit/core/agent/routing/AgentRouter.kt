package com.ai.assistance.operit.core.agent.routing

import com.ai.assistance.operit.core.agent.contract.AgentChatBindingSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentStatus

sealed interface AgentRoute {
    val chatId: String

    data class Legacy(override val chatId: String) : AgentRoute

    data class Plugin(
        override val chatId: String,
        val session: AgentSessionSnapshot,
    ) : AgentRoute
}

object AgentRouter {
    fun resolve(
        chatId: String,
        binding: AgentChatBindingSnapshot?,
        boundSession: AgentSessionSnapshot?,
    ): AgentRoute {
        require(chatId.isNotBlank()) { "chatId must not be blank" }
        return when (binding) {
            null -> {
                require(boundSession == null) { "Unbound chat must not provide an Agent session" }
                AgentRoute.Legacy(chatId)
            }

            else -> {
                val session = requireNotNull(boundSession) {
                    "Bound Agent session not found: ${binding.activeSessionId.value}"
                }
                require(binding.chatId == chatId) {
                    "Agent binding chat mismatch: ${binding.chatId} != $chatId"
                }
                require(session.chatId == chatId) {
                    "Agent session chat mismatch: ${session.chatId} != $chatId"
                }
                require(session.sessionId == binding.activeSessionId) {
                    "Agent binding session mismatch: ${binding.activeSessionId.value} != ${session.sessionId.value}"
                }
                require(session.parentSessionId == null && session.depth == 0) {
                    "Only a root Agent session can own a chat route"
                }
                require(
                    session.status != AgentStatus.COMPLETED &&
                        session.status != AgentStatus.FAILED &&
                        session.status != AgentStatus.CANCELLED
                ) {
                    "A terminal Agent session cannot own a chat route"
                }
                AgentRoute.Plugin(chatId = chatId, session = session)
            }
        }
    }
}
