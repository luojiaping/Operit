package com.ai.assistance.operit.core.agent.history

import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentOwner
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentHistoryAdapterTest {
    private val pluginOwner =
        AgentOwner.PluginAgent(
            pluginId = "toolpkg:opencode_agent",
            agentId = AgentId("build"),
            sessionId = AgentSessionId("session-build"),
        )

    @Test
    fun adapterUsesStableOrderAndExplicitOwners() {
        val items =
            AgentHistoryAdapter.adapt(
                listOf(
                    source(messageId = 30L, orderIndex = 2, sender = "ai", owner = pluginOwner),
                    source(messageId = 10L, orderIndex = 0, sender = "ai"),
                    source(messageId = 20L, orderIndex = 1, sender = "user"),
                    source(messageId = 40L, orderIndex = 2, sender = "summary", owner = pluginOwner),
                )
            )

        assertEquals(listOf(10L, 20L, 30L, 40L), items.map { it.messageId })
        assertEquals(AgentOwner.LegacyRoleCard, items[0].owner)
        assertEquals(AgentOwner.SharedUser, items[1].owner)
        assertEquals(pluginOwner, items[2].owner)
        assertEquals(AgentHistoryItemKind.SUMMARY, items[3].kind)
    }

    @Test
    fun userMessageCannotHavePluginOwner() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentHistoryAdapter.adapt(
                listOf(source(messageId = 1L, orderIndex = 0, sender = "user", owner = pluginOwner))
            )
        }
    }

    @Test
    fun unknownSenderIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentHistoryAdapter.adapt(
                listOf(source(messageId = 1L, orderIndex = 0, sender = "system"))
            )
        }
    }

    private fun source(
        messageId: Long,
        orderIndex: Int,
        sender: String,
        owner: AgentOwner.PluginAgent? = null,
    ): AgentHistorySourceMessage {
        return AgentHistorySourceMessage(
            messageId = messageId,
            timestamp = 100L,
            orderIndex = orderIndex,
            sender = sender,
            content = "$sender-$messageId",
            pluginOwner = owner,
        )
    }
}
