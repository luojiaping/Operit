package com.ai.assistance.operit.core.agent.registry

import com.ai.assistance.operit.core.agent.contract.AgentCapabilities
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgentPluginRegistrationCodecTest {
    @Test
    fun decodesStaticToolPkgProfileWithPackageOwner() {
        val registration =
            AgentPluginRegistrationCodec.decode(
                pluginId = "package.example",
                raw =
                    """
                    {
                      "agentId":"writer",
                      "displayName":"Writer",
                      "profileVersion":"1",
                      "profileKind":"PRIMARY",
                      "modeId":"text",
                      "promptKey":"writer.prompt",
                      "promptSnapshot":"Write text only.",
                      "capabilities":["${AgentCapabilities.RUNTIME_V1}"]
                    }
                    """.trimIndent(),
            )

        assertEquals("package.example", registration.declaration.pluginId)
        assertEquals("writer", registration.declaration.agentId.value)
        assertEquals(AgentProfileKind.PRIMARY, registration.declaration.profileKind)
        assertEquals("Write text only.", registration.promptSnapshot)
    }

    @Test
    fun rejectsToolPkgProfileWithoutRuntimeCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentPluginRegistrationCodec.decode(
                pluginId = "package.example",
                raw = """
                    {"agentId":"writer","displayName":"Writer","profileVersion":"1",
                     "profileKind":"PRIMARY","modeId":"text","promptKey":"writer.prompt",
                     "promptSnapshot":"Write text only.","capabilities":[]}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun rejectsToolPkgToolsUntilAgentToolContractExists() {
        assertThrows(IllegalArgumentException::class.java) {
            AgentPluginRegistrationCodec.decode(
                pluginId = "package.example",
                raw = """
                    {"agentId":"writer","displayName":"Writer","profileVersion":"1",
                     "profileKind":"PRIMARY","modeId":"text","promptKey":"writer.prompt",
                     "promptSnapshot":"Write text only.","capabilities":["${AgentCapabilities.RUNTIME_V1}"],
                     "tools":["read_file"]}
                """.trimIndent(),
            )
        }
    }
}
