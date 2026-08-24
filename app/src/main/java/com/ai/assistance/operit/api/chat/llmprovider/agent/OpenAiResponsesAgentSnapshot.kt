package com.ai.assistance.operit.api.chat.llmprovider.agent

import com.ai.assistance.operit.data.model.ApiKeyFormatValidator
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class OpenAiResponsesAgentSnapshot(
    val schema: String,
    val credentialRef: String,
    val model: String,
    val maxOutputTokens: Int?,
) {
    init {
        require(schema == SCHEMA_V1) { "Unsupported OpenAI Responses Agent snapshot schema" }
        requireSafeIdentifier(credentialRef, "credentialRef", MAX_CREDENTIAL_REF_LENGTH)
        requireSafeModel(model)
        require(maxOutputTokens == null || maxOutputTokens > 0) {
            "maxOutputTokens must be positive when specified"
        }
    }

    fun encode(): String = STRICT_JSON.encodeToString(this)

    companion object {
        const val SCHEMA_V1 = "operit.openai-responses-agent.v1"
        const val OFFICIAL_ENDPOINT = "https://api.openai.com/v1/responses"

        private const val MAX_SNAPSHOT_LENGTH = 1_024
        private const val MAX_CREDENTIAL_REF_LENGTH = 256
        private const val MAX_MODEL_LENGTH = 256

        private val STRICT_JSON =
            Json {
                ignoreUnknownKeys = false
                isLenient = false
                encodeDefaults = true
            }

        fun decode(raw: String): OpenAiResponsesAgentSnapshot {
            require(raw.length <= MAX_SNAPSHOT_LENGTH) { "OpenAI Responses Agent snapshot is too large" }
            return STRICT_JSON.decodeFromString(raw)
        }

        fun fromModelConfig(config: ModelConfigData, modelIndex: Int): OpenAiResponsesAgentSnapshot {
            OpenAiResponsesAgentConfigValidator.validateProviderBinding(config)
            OpenAiResponsesAgentConfigValidator.validateRuntimeOptions(config)
            require(modelIndex >= 0) { "OpenAI Responses Agent model index must not be negative" }
            val models = getModelList(config.modelName)
            require(modelIndex < models.size) { "OpenAI Responses Agent model index is out of range" }
            return OpenAiResponsesAgentSnapshot(
                schema = SCHEMA_V1,
                credentialRef = config.id,
                model = models[modelIndex],
                maxOutputTokens = if (config.maxTokensEnabled) config.maxTokens else null,
            )
        }

        private fun requireSafeIdentifier(value: String, field: String, maxLength: Int) {
            require(value.isNotBlank()) { "$field must not be blank" }
            require(value.length <= maxLength) { "$field exceeds the maximum length" }
            require(value.all { character -> character.code in 0x21..0x7E }) {
                "$field must contain visible ASCII characters only"
            }
        }

        private fun requireSafeModel(value: String) {
            requireSafeIdentifier(value, "model", MAX_MODEL_LENGTH)
            require(',' !in value) { "OpenAI Responses Agent model must be resolved to one model" }
        }
    }
}

class OpenAiResponsesAgentCredential internal constructor(
    internal val token: String,
) {
    override fun toString(): String = "OpenAiResponsesAgentCredential(redacted)"
}

fun interface OpenAiResponsesAgentCredentialProvider {
    suspend fun resolve(snapshot: OpenAiResponsesAgentSnapshot): OpenAiResponsesAgentCredential
}

class ModelConfigOpenAiResponsesAgentCredentialProvider(
    private val modelConfigManager: ModelConfigManager,
) : OpenAiResponsesAgentCredentialProvider {
    override suspend fun resolve(snapshot: OpenAiResponsesAgentSnapshot): OpenAiResponsesAgentCredential {
        val config = requireNotNull(modelConfigManager.getModelConfig(snapshot.credentialRef)) {
            "OpenAI Responses Agent credential configuration was not found"
        }
        require(config.id == snapshot.credentialRef) {
            "OpenAI Responses Agent credential configuration identity changed"
        }
        OpenAiResponsesAgentConfigValidator.validateProviderBinding(config)
        OpenAiResponsesAgentConfigValidator.validateRuntimeOptions(config)
        val token = ApiKeyFormatValidator.normalize(config.apiKey)
        require(ApiKeyFormatValidator.isValid(token)) { "OpenAI Responses Agent API key is invalid" }
        require(token.length <= 1_024) { "OpenAI Responses Agent API key exceeds the maximum length" }
        return OpenAiResponsesAgentCredential(token)
    }
}

object OpenAiResponsesAgentConfigValidator {
    fun validateProviderBinding(config: ModelConfigData) {
        require(config.apiProviderType == ApiProviderType.OPENAI_RESPONSES) {
            "OpenAI Responses Agent requires OPENAI_RESPONSES provider type"
        }
        require(config.apiProviderTypeId == ApiProviderType.OPENAI_RESPONSES.name) {
            "OpenAI Responses Agent requires canonical provider type ID"
        }
        require(config.apiEndpoint == OpenAiResponsesAgentSnapshot.OFFICIAL_ENDPOINT) {
            "OpenAI Responses Agent requires the official Responses endpoint"
        }
    }

    fun validateRuntimeOptions(config: ModelConfigData) {
        require(!config.useMultipleApiKeys) {
            "OpenAI Responses Agent does not support API key pools"
        }
        require(config.apiKeyPool.isEmpty()) {
            "OpenAI Responses Agent does not support configured API key pools"
        }
        require(!config.hasCustomParameters) {
            "OpenAI Responses Agent does not support custom parameters"
        }
        require(parseEmptyObject(config.customHeaders, "customHeaders")) {
            "OpenAI Responses Agent does not support custom headers"
        }
        require(parseEmptyArray(config.customParameters, "customParameters")) {
            "OpenAI Responses Agent does not support custom parameters"
        }
        require(!config.temperatureEnabled) {
            "OpenAI Responses Agent does not support temperature"
        }
        require(!config.topPEnabled) {
            "OpenAI Responses Agent does not support topP"
        }
        require(!config.topKEnabled) {
            "OpenAI Responses Agent does not support topK"
        }
        require(!config.presencePenaltyEnabled) {
            "OpenAI Responses Agent does not support presence penalty"
        }
        require(!config.frequencyPenaltyEnabled) {
            "OpenAI Responses Agent does not support frequency penalty"
        }
        require(!config.repetitionPenaltyEnabled) {
            "OpenAI Responses Agent does not support repetition penalty"
        }
    }

    private fun parseEmptyObject(raw: String, field: String): Boolean {
        val parsed = try {
            JSONObject(raw)
        } catch (exception: Exception) {
            throw IllegalArgumentException("OpenAI Responses Agent $field is not valid JSON", exception)
        }
        return !parsed.keys().hasNext()
    }

    private fun parseEmptyArray(raw: String, field: String): Boolean {
        val parsed = try {
            JSONArray(raw)
        } catch (exception: Exception) {
            throw IllegalArgumentException("OpenAI Responses Agent $field is not valid JSON", exception)
        }
        return parsed.length() == 0
    }
}
