package com.ai.assistance.operit.api.chat.llmprovider.agent

import com.ai.assistance.operit.core.agent.model.AgentModelResolution
import com.ai.assistance.operit.core.agent.model.AgentModelResolver
import com.ai.assistance.operit.data.preferences.ModelConfigManager

class OpenAiResponsesAgentModelResolver(
    private val modelConfigManager: ModelConfigManager,
    private val credentialProvider: OpenAiResponsesAgentCredentialProvider =
        ModelConfigOpenAiResponsesAgentCredentialProvider(modelConfigManager),
) : AgentModelResolver {
    override suspend fun resolve(modelConfigId: String, modelIndex: Int): AgentModelResolution {
        require(modelConfigId.isNotBlank()) { "OpenAI Responses Agent model config ID must not be blank" }
        require(modelIndex >= 0) { "OpenAI Responses Agent model index must not be negative" }
        val config = requireNotNull(modelConfigManager.getModelConfig(modelConfigId)) {
            "OpenAI Responses Agent model configuration was not found: $modelConfigId"
        }
        require(config.id == modelConfigId) {
            "OpenAI Responses Agent model configuration identity changed"
        }
        val snapshot = OpenAiResponsesAgentSnapshot.fromModelConfig(config, modelIndex)
        return AgentModelResolution(
            modelSnapshotJson = snapshot.encode(),
            client = OpenAiResponsesAgentModelClient(credentialProvider),
        )
    }
}
