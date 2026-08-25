package com.ai.assistance.operit.core.agent.registry

import com.ai.assistance.operit.core.agent.contract.AgentCapabilities
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentProfileDeclaration
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import com.ai.assistance.operit.plugins.OperitPlugin

object BuiltinTextAgentPlugin : OperitPlugin {
    const val PLUGIN_ID = "operit.agent"
    val AGENT_ID = AgentId("operit.primary")
    val MODE_ID = AgentModeId("text")
    const val DISPLAY_NAME = "Operit Agent"
    const val PROFILE_VERSION = "1"
    const val PROMPT_SNAPSHOT =
        "You are a text-only Agent runtime. Do not call tools, emit XML, or explain your process."

    override val id: String = "operit.agent.runtime"

    override fun register() {
        AgentPluginRegistry.global.registerIfAbsent(registration())
    }

    fun registration(): AgentPluginRegistration {
        return AgentPluginRegistration(
            declaration =
                AgentProfileDeclaration(
                    pluginId = PLUGIN_ID,
                    agentId = AGENT_ID,
                    displayName = DISPLAY_NAME,
                    profileVersion = PROFILE_VERSION,
                    profileKind = AgentProfileKind.PRIMARY,
                    modeId = MODE_ID,
                    promptKey = "operit.agent.text.v1",
                ),
            capabilities = setOf(AgentCapabilities.RUNTIME_V1),
            promptSnapshot = PROMPT_SNAPSHOT,
        )
    }
}
