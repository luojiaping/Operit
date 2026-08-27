package com.ai.assistance.operit.api.chat.llmprovider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import okhttp3.OkHttpClient

class OpenAIProviderNativeToolCallTest {
    private val provider =
        OpenAIProvider(
            apiEndpoint = "https://example.com/v1/chat/completions",
            apiKeyProvider = SingleApiKeyProvider("test-key"),
            modelName = "test-model",
            client = OkHttpClient(),
            enableToolCall = true,
            nativeToolCallMode = true
        )

    @Test
    fun `native parser keeps call id and raw argument json`() {
        val toolCall =
            provider.parseNativeToolCall(
                JSONObject(
                    """
                    {
                      "id": "call_abc",
                      "type": "function",
                      "function": {
                        "name": "read_file",
                        "arguments": "{\"path\":\"/tmp/a\",\"line\":3}"
                      }
                    }
                    """.trimIndent()
                ),
                index = 1
            )

        assertEquals("call_abc", toolCall.callId)
        assertEquals("read_file", toolCall.toolName)
        assertEquals("{\"path\":\"/tmp/a\",\"line\":3}", toolCall.argumentsJson)
        assertEquals(1, toolCall.index)
    }
}
