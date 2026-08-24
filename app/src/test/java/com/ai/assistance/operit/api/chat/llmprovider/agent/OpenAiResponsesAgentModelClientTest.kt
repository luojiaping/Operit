package com.ai.assistance.operit.api.chat.llmprovider.agent

import com.ai.assistance.operit.core.agent.contract.AgentModelRequestId
import com.ai.assistance.operit.core.agent.contract.AgentRunId
import com.ai.assistance.operit.core.agent.contract.AgentSessionId
import com.ai.assistance.operit.core.agent.contract.AgentStepId
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelErrorCode
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelMessage
import com.ai.assistance.operit.core.agent.model.AgentModelMessageRole
import com.ai.assistance.operit.core.agent.model.AgentModelRequest
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiResponsesAgentModelClientTest {
    @Test
    fun snapshotIsStrictAndDoesNotPersistCredential() {
        val snapshot =
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(modelName = "gpt-a,gpt-b", maxTokensEnabled = true, maxTokens = 321),
                1,
            )
        val encoded = snapshot.encode()

        assertEquals("gpt-b", snapshot.model)
        assertEquals(321, snapshot.maxOutputTokens)
        assertFalse(encoded.contains("secret-api-key"))
        assertFalse(encoded.contains("customHeaders"))
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.decode(
                """{"schema":"operit.openai-responses-agent.v1","credentialRef":"config","model":"gpt-a","extra":true}"""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(temperatureEnabled = true),
                0,
            )
        }
    }

    @Test
    fun configFactoryRejectsNonOfficialOrCustomizedResponsesConfig() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(providerType = ApiProviderType.OPENAI_RESPONSES_GENERIC),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(customHeaders = """{"X-Unsafe":"value"}"""),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(apiEndpoint = "https://example.invalid/v1/responses"),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(customParameters = """[{"temperature":0.1}]"""),
                0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(apiKeyPool = listOf(ApiKeyInfo(id = "pooled", key = "pooled-key"))),
                0,
            )
        }
    }

    @Test
    fun streamsTextReasoningUsageAndCompletedWithStrictRequest() = runBlocking {
        val captured = mutableListOf<String>()
        val client =
            modelClient(
                body = fixture("text_reasoning_usage_completed.sse"),
                onRequest = captured::add,
            )

        val events = collect(client, request())

        assertEquals(4, events.size)
        assertEquals("Hello", (events[0] as AgentModelEvent.TextDelta).text)
        assertEquals("Think", (events[1] as AgentModelEvent.ReasoningDelta).text)
        val usage = (events[2] as AgentModelEvent.Usage).usage
        assertEquals(5L, usage.inputTokens)
        assertEquals(1L, usage.cachedInputTokens)
        assertEquals(2L, usage.outputTokens)
        assertEquals(AgentModelStopReason.COMPLETE, (events[3] as AgentModelEvent.Completed).stopReason)
        assertEquals(listOf(0L, 1L, 2L, 3L), events.map(AgentModelEvent::eventIndex))

        val requestJson = JSONObject(captured.single())
        assertEquals("gpt-agent", requestJson.getString("model"))
        assertTrue(requestJson.getBoolean("stream"))
        assertFalse(requestJson.getBoolean("store"))
        assertFalse(requestJson.has("max_output_tokens"))
        assertEquals("developer", requestJson.getJSONArray("input").getJSONObject(0).getString("role"))
        assertEquals("assistant", requestJson.getJSONArray("input").getJSONObject(1).getString("role"))
        assertEquals("user", requestJson.getJSONArray("input").getJSONObject(2).getString("role"))
    }

    @Test
    fun frozenMaxTokensBecomesResponsesMaxOutputTokens() = runBlocking {
        val captured = mutableListOf<String>()
        val events =
            collect(
                modelClient(
                    body = fixture("text_reasoning_usage_completed.sse"),
                    onRequest = captured::add,
                ),
                request(maxOutputTokens = 321),
            )

        assertTrue(events.last() is AgentModelEvent.Completed)
        assertEquals(321, JSONObject(captured.single()).getInt("max_output_tokens"))
    }

    @Test
    fun preservesProviderCallIdForTypedToolEvent() = runBlocking {
        val client =
            modelClient(
                body = fixture("function_call_completed.sse"),
            )

        val events = collect(client, request())

        assertEquals(2, events.size)
        val toolCall = events[0] as AgentModelEvent.ToolCallReady
        assertEquals("call_server_1", toolCall.providerCallRef.value)
        assertEquals("read_file", toolCall.toolName)
        assertEquals("{\"path\":\"/tmp/a\"}", toolCall.argumentsJson)
        assertEquals(AgentModelStopReason.TOOL_CALL, (events[1] as AgentModelEvent.Completed).stopReason)
    }

    @Test
    fun providerErrorAndMissingTerminalProduceOneFailedEvent() = runBlocking {
        val providerErrorEvents =
            collect(
                modelClient(
                    body = fixture("provider_error.sse"),
                ),
                request(),
            )
        val providerFailure = providerErrorEvents.single() as AgentModelEvent.Failed
        assertEquals(AgentModelErrorCode.RATE_LIMIT, providerFailure.error.code)

        val incompleteEvents =
            collect(
                modelClient(
                    body = fixture("incomplete_stream.sse"),
                ),
                request(),
            )
        assertEquals(2, incompleteEvents.size)
        assertTrue(incompleteEvents[0] is AgentModelEvent.TextDelta)
        val incompleteFailure = incompleteEvents[1] as AgentModelEvent.Failed
        assertEquals(AgentModelErrorCode.PROTOCOL, incompleteFailure.error.code)
    }

    @Test
    fun incompleteResponseUsesLengthTerminalAndDoneMarkerFailsProtocol() = runBlocking {
        val incompleteEvents =
            collect(
                modelClient(body = fixture("response_incomplete.sse")),
                request(),
            )
        assertEquals(2, incompleteEvents.size)
        assertTrue(incompleteEvents[0] is AgentModelEvent.TextDelta)
        assertEquals(AgentModelStopReason.LENGTH, (incompleteEvents[1] as AgentModelEvent.Completed).stopReason)

        val doneMarkerEvents =
            collect(
                modelClient(body = fixture("done_marker.sse")),
                request(),
            )
        val failure = doneMarkerEvents.single() as AgentModelEvent.Failed
        assertEquals(AgentModelErrorCode.PROTOCOL, failure.error.code)
    }

    @Test
    fun contentFilterIncompleteResponseFailsInsteadOfCommittingPartialText() = runBlocking {
        val events =
            collect(
                modelClient(body = fixture("response_content_filter.sse")),
                request(),
            )

        assertEquals(2, events.size)
        assertTrue(events[0] is AgentModelEvent.TextDelta)
        val failure = events[1] as AgentModelEvent.Failed
        assertEquals(AgentModelErrorCode.PROVIDER, failure.error.code)
    }

    @Test
    fun cancellingOneRequestClosesOnlyItsSseCall() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val firstClosed = CompletableDeferred<Unit>()
        val requestCount = AtomicInteger()
        val firstBody = BlockingSseResponseBody(firstStarted, firstClosed)
        val httpClient =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val responseBody =
                            if (requestCount.getAndIncrement() == 0) {
                                firstBody
                            } else {
                                fixture("text_reasoning_usage_completed.sse")
                                    .toResponseBody("text/event-stream".toMediaType())
                            }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("Content-Type", "text/event-stream")
                            .body(responseBody)
                            .build()
                    },
                )
                .build()
        val client =
            OpenAiResponsesAgentModelClient(
                credentialProvider = OpenAiResponsesAgentCredentialProvider {
                    OpenAiResponsesAgentCredential("test-key")
                },
                httpClient = httpClient,
            )

        val firstJob = launch {
            client.execute(request("first")).collect { }
        }
        firstStarted.await()

        val secondEvents = async(Dispatchers.IO) { collect(client, request("second")) }.await()

        assertTrue(secondEvents.any { event -> event is AgentModelEvent.Completed })
        withTimeout(5_000L) {
            firstJob.cancelAndJoin()
            firstClosed.await()
        }
    }

    private suspend fun collect(
        client: AgentModelClient,
        request: AgentModelRequest,
    ): List<AgentModelEvent> {
        val events = mutableListOf<AgentModelEvent>()
        client.execute(request).collect(events::add)
        return events
    }

    private fun request(
        requestId: String = "request",
        maxOutputTokens: Int? = null,
    ): AgentModelRequest {
        val snapshot =
            OpenAiResponsesAgentSnapshot.fromModelConfig(
                config(
                    maxTokensEnabled = maxOutputTokens != null,
                    maxTokens = maxOutputTokens ?: 4096,
                ),
                0,
            )
        return AgentModelRequest(
            modelRequestId = AgentModelRequestId(requestId),
            sessionId = AgentSessionId("session"),
            runId = AgentRunId("run"),
            stepId = AgentStepId("step"),
            modelSnapshotJson = snapshot.encode(),
            systemPrompt = "system prompt",
            history =
                listOf(
                    AgentModelMessage(AgentModelMessageRole.ASSISTANT, "previous"),
                    AgentModelMessage(AgentModelMessageRole.USER, "question"),
                ),
            toolSnapshotJson = "[]",
        )
    }

    private fun modelClient(
        body: String,
        onRequest: (String) -> Unit = {},
    ): OpenAiResponsesAgentModelClient {
        val httpClient =
            OkHttpClient.Builder()
                .addInterceptor(
                    Interceptor { chain ->
                        val requestBody = requireNotNull(chain.request().body)
                        val buffer = Buffer()
                        requestBody.writeTo(buffer)
                        onRequest(buffer.readUtf8())
                        assertEquals(OpenAiResponsesAgentSnapshot.OFFICIAL_ENDPOINT, chain.request().url.toString())
                        assertEquals("Bearer test-key", chain.request().header("Authorization"))
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
        return OpenAiResponsesAgentModelClient(
            credentialProvider = OpenAiResponsesAgentCredentialProvider {
                OpenAiResponsesAgentCredential("test-key")
            },
            httpClient = httpClient,
        )
    }

    private fun config(
        modelName: String = "gpt-agent",
        providerType: ApiProviderType = ApiProviderType.OPENAI_RESPONSES,
        customHeaders: String = "{}",
        customParameters: String = "[]",
        apiEndpoint: String = OpenAiResponsesAgentSnapshot.OFFICIAL_ENDPOINT,
        apiKeyPool: List<ApiKeyInfo> = emptyList(),
        maxTokensEnabled: Boolean = false,
        maxTokens: Int = 4096,
        temperatureEnabled: Boolean = false,
    ): ModelConfigData {
        return ModelConfigData(
            id = "config",
            name = "OpenAI Responses",
            apiKey = "secret-api-key",
            apiEndpoint = apiEndpoint,
            modelName = modelName,
            apiProviderType = providerType,
            apiProviderTypeId = providerType.name,
            customHeaders = customHeaders,
            customParameters = customParameters,
            apiKeyPool = apiKeyPool,
            maxTokensEnabled = maxTokensEnabled,
            maxTokens = maxTokens,
            temperatureEnabled = temperatureEnabled,
        )
    }

    private fun fixture(name: String): String {
        val path = "com/ai/assistance/operit/api/chat/llmprovider/agent/fixtures/$name"
        return requireNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Missing OpenAI Responses fixture: $path"
        }.bufferedReader().use { reader -> reader.readText() }
    }
}

private class BlockingSseResponseBody(
    private val started: CompletableDeferred<Unit>,
    private val closed: CompletableDeferred<Unit>,
) : ResponseBody() {
    private val lock = java.lang.Object()
    private var isClosed = false
    private val source =
        object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (byteCount == 0L) {
                    return 0L
                }
                started.complete(Unit)
                synchronized(lock) {
                    while (!isClosed) {
                        try {
                            lock.wait()
                        } catch (interrupted: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw IOException("SSE source interrupted", interrupted)
                        }
                    }
                }
                throw IOException("SSE source closed")
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() {
                synchronized(lock) {
                    isClosed = true
                    lock.notifyAll()
                }
                closed.complete(Unit)
            }
        }
    private val bufferedSource: BufferedSource = source.buffer()

    override fun contentType(): MediaType? = "text/event-stream".toMediaType()

    override fun contentLength(): Long = -1L

    override fun source(): BufferedSource = bufferedSource

    override fun close() {
        source.close()
    }
}
