package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Structured tool records persisted alongside a chat message. */
@Serializable
data class NativeToolCallMetadata(
        val toolCalls: List<NativeToolCall> = emptyList(),
        val toolResults: List<NativeToolResult> = emptyList()
)

object NativeToolCallMetadataCodec {
    const val EMPTY_JSON = "{\"toolCalls\":[],\"toolResults\":[]}"

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(
            toolCalls: List<NativeToolCall> = emptyList(),
            toolResults: List<NativeToolResult> = emptyList()
    ): String {
        return json.encodeToString(NativeToolCallMetadata(toolCalls, toolResults))
    }

    fun decode(value: String): NativeToolCallMetadata? {
        if (value.isBlank()) {
            return null
        }
        return runCatching {
            json.decodeFromString<NativeToolCallMetadata>(value)
        }.getOrNull()
    }
}
