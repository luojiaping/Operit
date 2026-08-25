package com.ai.assistance.operit.core.agent.registry

import com.ai.assistance.operit.core.agent.contract.AgentCapabilities
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileDeclaration
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPluginRegistryTest {
    @Test
    fun registeredProfileCanBeResolvedByQualifiedIdentity() {
        val registry = AgentPluginRegistry()
        val registration = registration()

        registry.register(registration)

        assertEquals(
            registration,
            registry.resolve(
                pluginId = "plugin.example",
                agentId = AgentId("writer"),
                profileVersion = "1",
                modeId = AgentModeId("text"),
            )
        )
    }

    @Test
    fun duplicateQualifiedProfileIsRejected() {
        val registry = AgentPluginRegistry()
        registry.register(registration())

        assertThrows<IllegalArgumentException> {
            registry.register(registration())
        }
    }

    @Test
    fun missingRuntimeCapabilityIsRejected() {
        val registry = AgentPluginRegistry()

        assertThrows<IllegalArgumentException> {
            registry.register(
                registration(
                    capabilities = setOf(AgentCapabilities.UI_V1),
                )
            )
        }
    }

    @Test
    fun disabledProfileIsNotResolvedButCanBeReenabled() {
        val registry = AgentPluginRegistry()
        registry.register(registration())

        assertTrue(
            registry.setEnabled(
                "plugin.example",
                AgentId("writer"),
                "1",
                AgentModeId("text"),
                false,
            )
        )
        assertNull(
            registry.resolve(
                pluginId = "plugin.example",
                agentId = AgentId("writer"),
                profileVersion = "1",
                modeId = AgentModeId("text"),
            )
        )

        assertTrue(
            registry.setEnabled(
                "plugin.example",
                AgentId("writer"),
                "1",
                AgentModeId("text"),
                true,
            )
        )
        assertFalse(
            registry.setEnabled(
                "missing",
                AgentId("writer"),
                "1",
                AgentModeId("text"),
                false,
            )
        )
        assertEquals(
            "plugin.example",
            registry.requireEnabled(
                "plugin.example",
                AgentId("writer"),
                "1",
                AgentModeId("text"),
            ).declaration.pluginId
        )
    }

    private fun registration(
        capabilities: Set<String> = setOf(AgentCapabilities.RUNTIME_V1),
    ): AgentPluginRegistration {
        return AgentPluginRegistration(
            declaration =
                AgentProfileDeclaration(
                    pluginId = "plugin.example",
                    agentId = AgentId("writer"),
                    displayName = "Writer",
                    profileVersion = "1",
                    profileKind = AgentProfileKind.PRIMARY,
                    modeId = AgentModeId("text"),
                    promptKey = "writer.prompt",
                ),
            capabilities = capabilities,
            promptSnapshot = "writer prompt",
        )
    }

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
        var thrown = false
        try {
            block()
        } catch (error: Throwable) {
            assertTrue(error is T)
            thrown = true
        }
        assertTrue(thrown)
    }
}
