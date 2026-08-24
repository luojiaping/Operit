package com.ai.assistance.operit.api.chat.llmprovider.agent

import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.kernel.AgentKernel
import com.ai.assistance.operit.core.agent.kernel.AgentKernelCommand
import com.ai.assistance.operit.core.agent.kernel.AgentKernelEvent
import com.ai.assistance.operit.core.agent.kernel.IncrementingClock
import com.ai.assistance.operit.core.agent.kernel.RecordingKernelStore
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponsesAgentKernelIntegrationTest {
    @Test
    fun typedResponsesStreamCommitsThroughTextOnlyKernel() = runBlocking {
        val snapshot =
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                ModelConfigData(
                    id = "config",
                    name = "OpenAI Responses",
                    apiKey = "synthetic-key",
                    apiEndpoint = OpenAiResponsesAgentSnapshot.OFFICIAL_ENDPOINT,
                    modelName = "gpt-agent",
                    apiProviderType = ApiProviderType.OPENAI_RESPONSES,
                    apiProviderTypeId = ApiProviderType.OPENAI_RESPONSES.name,
                ),
                0,
            )
        val adapter =
            OpenAiResponsesAgentModelClient(
                credentialProvider = OpenAiResponsesAgentCredentialProvider {
                    OpenAiResponsesAgentCredential("test-key")
                },
                httpClient = respondingClient(fixture("text_reasoning_usage_completed.sse")),
            )
        val store = RecordingKernelStore()
        val events = mutableListOf<AgentKernelEvent>()

        AgentKernel(store, adapter, IncrementingClock())
            .execute(
                AgentKernelCommand(
                    sessionId = AgentSessionId("session"),
                    userText = "question",
                    userTimestamp = 100L,
                    promptSnapshot = "system prompt",
                    modelSnapshotJson = snapshot.encode(),
                    permissionSnapshotJson = "[]",
                    runId = AgentRunId("run"),
                    stepId = AgentStepId("step"),
                    modelRequestId = AgentModelRequestId("request"),
                )
            )
            .collect(events::add)

        assertEquals("Hello", store.completedRequest?.assistantText)
        assertEquals("Think", store.completedRequest?.reasoningText)
        assertTrue(events.any { event -> event is AgentKernelEvent.RunCompleted })
    }

    private fun respondingClient(body: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Type", "text/event-stream")
                        .body(body.toResponseBody("text/event-stream".toMediaType()))
                        .build()
                },
            )
            .build()
    }

    private fun fixture(name: String): String {
        val path = "com/ai/assistance/operit/api/chat/llmprovider/agent/fixtures/$name"
        return requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing OpenAI Responses fixture: $path"
        }.bufferedReader().use { reader -> reader.readText() }
    }
}
