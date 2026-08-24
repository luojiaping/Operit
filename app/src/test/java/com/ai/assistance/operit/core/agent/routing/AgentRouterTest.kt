package com.ai.assistance.operit.core.agent.routing

import com.ai.assistance.operit.core.agent.contract.AgentChatBindingSnapshot
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentSessionStatus
import com.ai.assistance.operit.core.agent.contract.AgentSessionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentRouterTest {
    @Test
    fun unboundChatUsesLegacyRoute() {
        val route = AgentRouter.resolve(chatId = "chat-1", binding = null, boundSession = null)

        assertEquals(AgentRoute.Legacy("chat-1"), route)
    }

    @Test
    fun explicitRootBindingUsesPluginRoute() {
        val session = session(chatId = "chat-1", sessionId = "root")
        val route =
            AgentRouter.resolve(
                chatId = "chat-1",
                binding = binding(chatId = "chat-1", sessionId = "root"),
                boundSession = session,
            )

        assertTrue(route is AgentRoute.Plugin)
        assertEquals(session, (route as AgentRoute.Plugin).session)
    }

    @Test
    fun childSessionCannotOwnChatRoute() {
        val child =
            session(
                chatId = "chat-1",
                sessionId = "child",
                parentSessionId = AgentSessionId("root"),
                depth = 1,
            )

        assertThrows(IllegalArgumentException::class.java) {
            AgentRouter.resolve(
                chatId = "chat-1",
                binding = binding(chatId = "chat-1", sessionId = "child"),
                boundSession = child,
            )
        }
    }

    @Test
    fun crossChatBindingIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentRouter.resolve(
                chatId = "chat-1",
                binding = binding(chatId = "chat-2", sessionId = "root"),
                boundSession = session(chatId = "chat-2", sessionId = "root"),
            )
        }
    }

    @Test
    fun terminalSessionCannotOwnChatRoute() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentRouter.resolve(
                chatId = "chat-1",
                binding = binding(chatId = "chat-1", sessionId = "root"),
                boundSession =
                    session(
                        chatId = "chat-1",
                        sessionId = "root",
                        status = AgentSessionStatus.COMPLETED,
                    ),
            )
        }
    }

    private fun binding(chatId: String, sessionId: String): AgentChatBindingSnapshot {
        return AgentChatBindingSnapshot(
            chatId = chatId,
            activeSessionId = AgentSessionId(sessionId),
            updatedAt = 10L,
        )
    }

    private fun session(
        chatId: String,
        sessionId: String,
        parentSessionId: AgentSessionId? = null,
        depth: Int = 0,
        status: AgentSessionStatus = AgentSessionStatus.IDLE,
    ): AgentSessionSnapshot {
        return AgentSessionSnapshot(
            sessionId = AgentSessionId(sessionId),
            chatId = chatId,
            pluginId = "toolpkg:opencode_agent",
            agentId = AgentId("build"),
            displayName = "Build",
            profileVersion = "1",
            profileKind = AgentProfileKind.PRIMARY,
            modeId = AgentModeId("build"),
            parentSessionId = parentSessionId,
            depth = depth,
            status = status,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}
