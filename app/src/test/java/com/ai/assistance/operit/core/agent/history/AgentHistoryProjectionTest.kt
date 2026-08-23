package com.ai.assistance.operit.core.agent.history

import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentHistoryProjectionTest {
    private val sessionId = AgentSessionId("session-build")
    private val otherSessionId = AgentSessionId("session-other")
    private val agentOwner = AgentOwner.PluginAgent(
        pluginId = "toolpkg:opencode_agent",
        agentId = AgentId("build"),
        sessionId = sessionId
    )
    private val otherAgentOwner = AgentOwner.PluginAgent(
        pluginId = "toolpkg:opencode_agent",
        agentId = AgentId("explore"),
        sessionId = otherSessionId
    )

    private val items = listOf(
        AgentHistoryItem(
            timestamp = 1L,
            kind = AgentHistoryItemKind.ASSISTANT,
            content = "role opening",
            owner = AgentOwner.LegacyRoleCard
        ),
        AgentHistoryItem(
            timestamp = 2L,
            kind = AgentHistoryItemKind.USER,
            content = "fix the bug",
            owner = AgentOwner.SharedUser
        ),
        AgentHistoryItem(
            timestamp = 2L,
            kind = AgentHistoryItemKind.ASSISTANT,
            content = "shared assistant must not exist",
            owner = AgentOwner.SharedUser
        ),
        AgentHistoryItem(
            timestamp = 3L,
            kind = AgentHistoryItemKind.SUMMARY,
            content = "legacy summary",
            owner = AgentOwner.LegacyRoleCard
        ),
        AgentHistoryItem(
            timestamp = 4L,
            kind = AgentHistoryItemKind.ASSISTANT,
            content = "build response",
            owner = agentOwner
        ),
        AgentHistoryItem(
            timestamp = 5L,
            kind = AgentHistoryItemKind.TOOL_RESULT,
            content = "build tool result",
            owner = agentOwner
        ),
        AgentHistoryItem(
            timestamp = 6L,
            kind = AgentHistoryItemKind.ASSISTANT,
            content = "other agent response",
            owner = otherAgentOwner
        )
    )

    @Test
    fun legacyProjectionExcludesPluginAgentItems() {
        val projection = AgentHistoryProjection.forLegacy(items)

        assertEquals(listOf("role opening", "fix the bug", "legacy summary"), projection.map { it.content })
    }

    @Test
    fun pluginProjectionKeepsSharedUserAndSelectedSession() {
        val projection = AgentHistoryProjection.forPluginAgent(items, sessionId)

        assertEquals(listOf("fix the bug", "build response", "build tool result"), projection.map { it.content })
    }

    @Test
    fun pluginOwnerKeyUsesSessionIdentity() {
        assertEquals("agent:session-build", agentOwner.key)
        assertEquals("agent:session-other", otherAgentOwner.key)
    }
}
