package com.ai.assistance.operit.core.agent.registry

import com.ai.assistance.operit.core.agent.contract.AgentCapabilities
import com.ai.assistance.operit.core.agent.contract.AgentId
import com.ai.assistance.operit.core.agent.contract.AgentModeId
import com.ai.assistance.operit.core.agent.contract.AgentPermissionRule
import com.ai.assistance.operit.core.agent.contract.AgentProfileDeclaration
import com.ai.assistance.operit.core.agent.contract.AgentProfileKind
import org.json.JSONArray
import org.json.JSONObject

object AgentPluginRegistrationCodec {
    fun decode(pluginId: String, raw: String): AgentPluginRegistration {
        require(pluginId.isNotBlank()) { "Agent plugin ID must not be blank" }
        val json = JSONObject(raw)
        val capabilities = parseStringArray(json, "capabilities").toSet()
        val permissions = parseStringArray(json, "permissions")
        val tools = parseStringArray(json, "tools")
        require(permissions.isEmpty()) {
            "ToolPkg Agent permissions require the Agent permission bridge"
        }
        require(tools.isEmpty()) {
            "ToolPkg Agent tools require the Agent tool bridge"
        }
        val declaration =
            AgentProfileDeclaration(
                pluginId = pluginId,
                agentId = AgentId(requiredString(json, "agentId")),
                displayName = requiredString(json, "displayName"),
                profileVersion = requiredString(json, "profileVersion"),
                profileKind = parseProfileKind(json.optString("profileKind", "PRIMARY")),
                modeId = AgentModeId(requiredString(json, "modeId")),
                promptKey = requiredString(json, "promptKey"),
                requestedPermissions = emptyList<AgentPermissionRule>(),
                toolIds = emptyList(),
                maxSteps = json.optInt("maxSteps", 1),
                canSpawnChildren = json.optBoolean("canSpawnChildren", false),
            )
        return AgentPluginRegistration(
            declaration = declaration,
            capabilities = capabilities,
            promptSnapshot = requiredString(json, "promptSnapshot"),
            permissionSnapshotJson = "[]",
            toolSnapshotJson = "[]",
        )
    }

    private fun requiredString(json: JSONObject, field: String): String {
        return json.optString(field, "").trim().also { value ->
            require(value.isNotBlank()) { "Agent registration $field must not be blank" }
        }
    }

    private fun parseStringArray(json: JSONObject, field: String): List<String> {
        val array = json.optJSONArray(field) ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index, "").trim()
                require(value.isNotBlank()) {
                    "Agent registration $field[$index] must not be blank"
                }
                add(value)
            }
        }
    }

    private fun parseProfileKind(raw: String): AgentProfileKind {
        return try {
            AgentProfileKind.valueOf(raw.trim().uppercase())
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported Agent registration profileKind: $raw", error)
        }
    }
}
