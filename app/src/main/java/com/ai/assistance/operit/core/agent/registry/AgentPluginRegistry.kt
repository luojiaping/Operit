package com.ai.assistance.operit.core.agent.registry

import com.ai.assistance.operit.core.agent.contract.AgentCapabilities
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentProfileDeclaration
import com.ai.assistance.operit.core.agent.contract.AgentModeId

data class AgentPluginRegistration(
    val declaration: AgentProfileDeclaration,
    val capabilities: Set<String>,
    val promptSnapshot: String,
    val permissionSnapshotJson: String = "[]",
    val toolSnapshotJson: String = "[]",
    val enabled: Boolean = true,
) {
    init {
        require(AgentCapabilities.RUNTIME_V1 in capabilities) {
            "Agent registration requires ${AgentCapabilities.RUNTIME_V1}"
        }
        require(capabilities.all(String::isNotBlank)) {
            "Agent registration capabilities must not be blank"
        }
        require(promptSnapshot.isNotBlank()) { "Agent registration prompt must not be blank" }
        require(permissionSnapshotJson.isNotBlank()) {
            "Agent registration permission snapshot must not be blank"
        }
        require(toolSnapshotJson.isNotBlank()) {
            "Agent registration tool snapshot must not be blank"
        }
    }
}

class AgentPluginRegistry {
    private val registrations = linkedMapOf<AgentPluginKey, AgentPluginRegistration>()

    @Synchronized
    fun register(registration: AgentPluginRegistration) {
        val key = AgentPluginKey.from(registration.declaration)
        require(key !in registrations) {
            "Agent profile is already registered: ${key.describe()}"
        }
        registrations[key] = registration
    }

    @Synchronized
    fun registerIfAbsent(registration: AgentPluginRegistration): Boolean {
        val key = AgentPluginKey.from(registration.declaration)
        if (key in registrations) {
            return false
        }
        registrations[key] = registration
        return true
    }

    @Synchronized
    fun resolve(
        pluginId: String,
        agentId: AgentId,
        profileVersion: String,
        modeId: AgentModeId,
    ): AgentPluginRegistration? {
        val key =
            AgentPluginKey(
                pluginId = pluginId,
                agentId = agentId.value,
                profileVersion = profileVersion,
                modeId = modeId.value,
            )
        return registrations[key]?.takeIf(AgentPluginRegistration::enabled)
    }

    @Synchronized
    fun requireEnabled(
        pluginId: String,
        agentId: AgentId,
        profileVersion: String,
        modeId: AgentModeId,
    ): AgentPluginRegistration {
        val registration = registrations.entries.firstOrNull { (key, value) ->
            key.pluginId == pluginId &&
                key.agentId == agentId.value &&
                key.profileVersion == profileVersion &&
                key.modeId == modeId.value &&
                value.enabled
        }?.value
        return requireNotNull(registration) {
            "Enabled Agent profile was not found: $pluginId/${agentId.value}/$profileVersion"
        }
    }

    @Synchronized
    fun setEnabled(
        pluginId: String,
        agentId: AgentId,
        profileVersion: String,
        modeId: AgentModeId,
        enabled: Boolean,
    ): Boolean {
        val entry = registrations.entries.firstOrNull { (key, _) ->
                key.pluginId == pluginId &&
                key.agentId == agentId.value &&
                key.profileVersion == profileVersion &&
                key.modeId == modeId.value
        } ?: return false
        registrations[entry.key] = entry.value.copy(enabled = enabled)
        return true
    }

    @Synchronized
    fun allEnabled(): List<AgentPluginRegistration> {
        return registrations.values.filter(AgentPluginRegistration::enabled)
    }

    companion object {
        val global: AgentPluginRegistry = AgentPluginRegistry()
    }
}

private data class AgentPluginKey(
    val pluginId: String,
    val agentId: String,
    val profileVersion: String,
    val modeId: String,
) {
    init {
        require(pluginId.isNotBlank()) { "Agent plugin ID must not be blank" }
        require(agentId.isNotBlank()) { "Agent ID must not be blank" }
        require(profileVersion.isNotBlank()) { "Agent profile version must not be blank" }
        require(modeId.isNotBlank()) { "Agent mode ID must not be blank" }
    }

    fun describe(): String = "$pluginId/$agentId/$profileVersion/$modeId"

    companion object {
        fun from(declaration: AgentProfileDeclaration): AgentPluginKey {
            return AgentPluginKey(
                pluginId = declaration.pluginId,
                agentId = declaration.agentId.value,
                profileVersion = declaration.profileVersion,
                modeId = declaration.modeId.value,
            )
        }
    }
}
