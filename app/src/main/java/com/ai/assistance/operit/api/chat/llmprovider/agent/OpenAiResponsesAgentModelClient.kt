package com.ai.assistance.operit.api.chat.llmprovider.agent

import com.ai.assistance.operit.api.chat.llmprovider.OpenAIResponsesPayloadAdapter
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelError
import com.ai.assistance.operit.core.agent.model.AgentModelErrorCode
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelMessageRole
import com.ai.assistance.operit.core.agent.model.AgentModelRequest
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.core.agent.model.AgentModelUsage
import com.ai.assistance.operit.core.agent.contract.ProviderToolCallRef
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiResponsesAgentModelClient(
    private val credentialProvider: OpenAiResponsesAgentCredentialProvider,
    private val httpClient: OkHttpClient = createDefaultHttpClient(),
) : AgentModelClient {
    override fun execute(request: AgentModelRequest): Flow<AgentModelEvent> =
        channelFlow {
            val producer = this
            val emitter = TypedEventEmitter(request, producer)
            val localCall =
                try {
                    val snapshot = OpenAiResponsesAgentSnapshot.decode(request.modelSnapshotJson)
                    validateToolSnapshot(request.toolSnapshotJson)
                    val credential = credentialProvider.resolve(snapshot)
                    httpClient.newCall(buildHttpRequest(request, snapshot, credential))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (configuration: IllegalArgumentException) {
                    emitter.fail(
                        AgentModelError(
                            code = AgentModelErrorCode.INVALID_REQUEST,
                            message = configuration.message ?: "OpenAI Responses Agent configuration is invalid",
                            retryable = false,
                        )
                    )
                    producer.close()
                    return@channelFlow
                } catch (_: Exception) {
                    emitter.fail(protocolError("OpenAI Responses adapter failed while preparing one request"))
                    producer.close()
                    return@channelFlow
                }
            val worker =
                launch(Dispatchers.IO) {
                    try {
                        localCall.execute().use { response ->
                            if (!response.isSuccessful) {
                                emitter.fail(httpError(response.code))
                                return@use
                            }
                            val body = response.body
                            if (body == null) {
                                emitter.fail(protocolError("OpenAI Responses returned an empty response body"))
                                return@use
                            }
                            body.charStream().buffered().use { reader ->
                                consumeSse(reader, emitter)
                            }
                        }
                        if (!emitter.isTerminal) {
                            emitter.fail(protocolError("OpenAI Responses stream ended without a terminal event"))
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (configuration: IllegalArgumentException) {
                        emitter.fail(
                            AgentModelError(
                                code = AgentModelErrorCode.INVALID_REQUEST,
                                message = configuration.message ?: "OpenAI Responses Agent configuration is invalid",
                                retryable = false,
                            )
                        )
                    } catch (protocol: OpenAiResponsesProtocolException) {
                        emitter.fail(protocolError(protocol.message ?: "OpenAI Responses protocol is invalid"))
                    } catch (network: IOException) {
                        if (!currentCoroutineContext().isActive) {
                            throw CancellationException("OpenAI Responses request cancelled", network)
                        }
                        emitter.fail(
                            AgentModelError(
                                code = AgentModelErrorCode.NETWORK,
                                message = "OpenAI Responses network request failed",
                                retryable = true,
                            )
                        )
                    } catch (_: Exception) {
                        emitter.fail(protocolError("OpenAI Responses adapter failed while processing one request"))
                    } finally {
                        producer.close()
                    }
                }
            awaitClose {
                localCall.cancel()
                worker.cancel()
            }
        }

    private suspend fun consumeSse(
        reader: BufferedReader,
        emitter: TypedEventEmitter,
    ) {
        val parser = ResponsesSseParser(emitter)
        var terminal = false
        while (!terminal) {
            val line = reader.readLine() ?: break
            terminal = parser.acceptLine(line)
        }
        if (!terminal) {
            terminal = parser.finish()
        }
    }

    private fun buildHttpRequest(
        request: AgentModelRequest,
        snapshot: OpenAiResponsesAgentSnapshot,
        credential: OpenAiResponsesAgentCredential,
    ): Request {
        val input = JSONArray()
        input.put(
            JSONObject().apply {
                put("role", "developer")
                put("content", request.systemPrompt)
            }
        )
        request.history.forEach { message ->
            input.put(
                JSONObject().apply {
                    put(
                        "role",
                        when (message.role) {
                            AgentModelMessageRole.USER -> "user"
                            AgentModelMessageRole.ASSISTANT -> "assistant"
                        },
                    )
                    put("content", message.content)
                }
            )
        }
        val requestJson =
            JSONObject().apply {
                put("model", snapshot.model)
                put("stream", true)
                put("store", false)
                put("input", input)
                snapshot.maxOutputTokens?.let { maxOutputTokens ->
                    put("max_output_tokens", maxOutputTokens)
                }
            }
        return Request.Builder()
            .url(OpenAiResponsesAgentSnapshot.OFFICIAL_ENDPOINT)
            .header("Authorization", "Bearer ${credential.token}")
            .header("Accept", "text/event-stream")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun validateToolSnapshot(raw: String) {
        val tools = try {
            JSONArray(raw)
        } catch (exception: Exception) {
            throw IllegalArgumentException("OpenAI Responses Agent tool snapshot is not valid JSON", exception)
        }
        require(tools.length() == 0) { "Text-only OpenAI Responses Agent does not support tools" }
    }

    private fun httpError(statusCode: Int): AgentModelError {
        val code =
            when (statusCode) {
                401, 403 -> AgentModelErrorCode.AUTHENTICATION
                429 -> AgentModelErrorCode.RATE_LIMIT
                in 400..499 -> AgentModelErrorCode.INVALID_REQUEST
                else -> AgentModelErrorCode.PROVIDER
            }
        return AgentModelError(
            code = code,
            message = "OpenAI Responses request failed with HTTP $statusCode",
            retryable = statusCode == 429 || statusCode >= 500,
        )
    }

    private fun protocolError(message: String): AgentModelError {
        return AgentModelError(
            code = AgentModelErrorCode.PROTOCOL,
            message = message,
            retryable = false,
        )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun createDefaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }
}

private class TypedEventEmitter(
    private val request: AgentModelRequest,
    private val scope: kotlinx.coroutines.channels.ProducerScope<AgentModelEvent>,
) {
    private var nextEventIndex = 0L
    var isTerminal: Boolean = false
        private set

    suspend fun text(text: String) {
        require(text.isNotEmpty()) { "OpenAI Responses text delta must not be empty" }
        emit(AgentModelEvent.TextDelta(request.modelRequestId, nextIndex(), text))
    }

    suspend fun reasoning(text: String) {
        require(text.isNotEmpty()) { "OpenAI Responses reasoning delta must not be empty" }
        emit(AgentModelEvent.ReasoningDelta(request.modelRequestId, nextIndex(), text))
    }

    suspend fun toolCall(
        callRef: ProviderToolCallRef,
        ordinal: Int,
        name: String,
        argumentsJson: String,
    ) {
        emit(
            AgentModelEvent.ToolCallReady(
                modelRequestId = request.modelRequestId,
                eventIndex = nextIndex(),
                providerCallRef = callRef,
                ordinal = ordinal,
                toolName = name,
                argumentsJson = argumentsJson,
            )
        )
    }

    suspend fun usage(usage: AgentModelUsage) {
        emit(AgentModelEvent.Usage(request.modelRequestId, nextIndex(), usage))
    }

    suspend fun complete(stopReason: AgentModelStopReason) {
        emit(AgentModelEvent.Completed(request.modelRequestId, nextIndex(), stopReason), terminal = true)
    }

    suspend fun fail(error: AgentModelError) {
        if (isTerminal) {
            return
        }
        emit(AgentModelEvent.Failed(request.modelRequestId, nextIndex(), error), terminal = true)
    }

    private suspend fun emit(event: AgentModelEvent, terminal: Boolean = false) {
        check(!isTerminal) { "OpenAI Responses emitted an event after terminal" }
        scope.send(event)
        if (terminal) {
            isTerminal = true
        }
    }

    private fun nextIndex(): Long {
        val current = nextEventIndex
        nextEventIndex += 1L
        return current
    }
}

private class ResponsesSseParser(
    private val emitter: TypedEventEmitter,
) {
    private val dataLines = mutableListOf<String>()
    private val functionCalls = linkedMapOf<Int, PendingFunctionCall>()
    private var emittedTextLength = 0
    private var emittedReasoningLength = 0

    suspend fun acceptLine(line: String): Boolean {
        if (line.isEmpty()) {
            return dispatchFrame()
        }
        if (line.startsWith("data:")) {
            dataLines += line.removePrefix("data:").removePrefix(" ")
        }
        return false
    }

    suspend fun finish(): Boolean = dispatchFrame()

    private suspend fun dispatchFrame(): Boolean {
        if (dataLines.isEmpty()) {
            return false
        }
        val payload = dataLines.joinToString("\n")
        dataLines.clear()
        if (payload == "[DONE]") {
            throw OpenAiResponsesProtocolException(
                "OpenAI Responses stream used an unsupported Chat Completions terminal marker"
            )
        }
        val event = try {
            JSONObject(payload)
        } catch (exception: Exception) {
            throw OpenAiResponsesProtocolException("OpenAI Responses SSE frame is not valid JSON", exception)
        }
        val type = event.optString("type", "")
        if (type.isBlank() && event.has("error")) {
            emitter.fail(providerError(event))
            return true
        }
        if (type.isBlank()) {
            throw OpenAiResponsesProtocolException("OpenAI Responses SSE frame has no type")
        }
        return when (type) {
            "response.output_text.delta", "response.refusal.delta" -> {
                val delta = event.optString("delta", "")
                if (delta.isNotEmpty()) {
                    emitter.text(delta)
                    emittedTextLength += delta.length
                }
                false
            }

            "response.reasoning_text.delta", "response.reasoning_summary_text.delta" -> {
                val delta = event.optString("delta", "")
                if (delta.isNotEmpty()) {
                    emitter.reasoning(delta)
                    emittedReasoningLength += delta.length
                }
                false
            }

            "response.reasoning_text.done", "response.reasoning_summary_text.done" -> {
                val text = event.optString("text", "")
                if (text.isNotEmpty() && emittedReasoningLength == 0) {
                    emitter.reasoning(text)
                    emittedReasoningLength += text.length
                }
                false
            }

            "response.reasoning_summary_part.done" -> {
                val text = event.optJSONObject("part")?.optString("text", "") ?: ""
                if (text.isNotEmpty() && emittedReasoningLength == 0) {
                    emitter.reasoning(text)
                    emittedReasoningLength += text.length
                }
                false
            }

            "response.output_item.added", "response.output_item.done" -> {
                mergeOutputItem(event)
                false
            }

            "response.function_call_arguments.delta" -> {
                val call = functionCallFor(event)
                call.mergeDelta(event.optString("delta", ""))
                false
            }

            "response.function_call_arguments.done" -> {
                val call = functionCallFor(event)
                call.mergeFinalArguments(event.optString("arguments", ""))
                emitToolCall(call)
                false
            }

            "response.output_text.done",
            "response.refusal.done",
            "response.content_part.added",
            "response.content_part.done",
            "response.reasoning_summary_part.added",
            "response.created",
            "response.queued",
            "response.in_progress" -> false

            "response.completed" -> {
                completeResponse(requireResponse(event), forcedStopReason = null)
            }

            "response.incomplete" -> {
                val response = requireResponse(event)
                val reason = response.optJSONObject("incomplete_details")?.optString("reason", "").orEmpty()
                if (reason == "max_output_tokens") {
                    completeResponse(
                        response = response,
                        forcedStopReason = AgentModelStopReason.LENGTH,
                    )
                } else {
                    if (reason.isBlank()) {
                        throw OpenAiResponsesProtocolException(
                            "OpenAI Responses incomplete event has no incomplete reason"
                        )
                    }
                    emitter.fail(
                        AgentModelError(
                            code = AgentModelErrorCode.PROVIDER,
                            message = "OpenAI Responses ended incomplete: $reason",
                            retryable = false,
                        )
                    )
                    true
                }
            }

            "response.failed", "response.error", "error" -> {
                emitter.fail(providerError(event))
                true
            }

            else -> throw OpenAiResponsesProtocolException("Unsupported OpenAI Responses SSE event: $type")
        }
    }

    private fun mergeOutputItem(event: JSONObject) {
        val item = event.optJSONObject("item") ?: return
        if (item.optString("type", "") != "function_call") {
            return
        }
        val index = event.optInt("output_index", -1)
        if (index < 0) {
            throw OpenAiResponsesProtocolException("OpenAI Responses function call has no output index")
        }
        val call = functionCalls.getOrPut(index) { PendingFunctionCall(index) }
        call.mergeMetadata(
            callId = item.optString("call_id", ""),
            name = item.optString("name", ""),
        )
        call.mergeFinalArguments(item.optString("arguments", ""))
    }

    private fun functionCallFor(event: JSONObject): PendingFunctionCall {
        val index = event.optInt("output_index", -1)
        if (index < 0) {
            throw OpenAiResponsesProtocolException("OpenAI Responses function call arguments have no output index")
        }
        val call = functionCalls.getOrPut(index) { PendingFunctionCall(index) }
        call.mergeMetadata(
            callId = event.optString("call_id", ""),
            name = event.optString("name", ""),
        )
        return call
    }

    private suspend fun completeResponse(
        response: JSONObject,
        forcedStopReason: AgentModelStopReason?,
    ): Boolean {
        mergeCompletedResponse(response)
        emitFinalOutputText(response)
        emitFinalReasoning(response)
        val usage = OpenAIResponsesPayloadAdapter.parseUsageCounts(response.optJSONObject("usage"))
        if (usage != null) {
            emitter.usage(
                AgentModelUsage(
                    inputTokens = usage.totalInputTokens,
                    cachedInputTokens = usage.cachedInputTokens,
                    outputTokens = usage.outputTokens,
                )
            )
        }
        for (call in functionCalls.values.sortedBy(PendingFunctionCall::ordinal)) {
            emitToolCall(call)
        }
        emitter.complete(
            forcedStopReason
                ?: when {
                    functionCalls.isNotEmpty() -> AgentModelStopReason.TOOL_CALL
                    response.optString("status", "") == "incomplete" -> AgentModelStopReason.LENGTH
                    else -> AgentModelStopReason.COMPLETE
                }
        )
        return true
    }

    private suspend fun emitToolCall(call: PendingFunctionCall) {
        if (call.emitted) {
            return
        }
        val callId = call.callId?.takeIf(String::isNotBlank)
            ?: throw OpenAiResponsesProtocolException("OpenAI Responses function call has no call ID")
        val name = call.name?.takeIf(String::isNotBlank)
            ?: throw OpenAiResponsesProtocolException("OpenAI Responses function call has no name")
        val arguments = call.arguments.takeIf(String::isNotBlank)
            ?: throw OpenAiResponsesProtocolException("OpenAI Responses function call has no arguments")
        try {
            JSONObject(arguments)
        } catch (exception: Exception) {
            throw OpenAiResponsesProtocolException("OpenAI Responses function arguments are not a JSON object", exception)
        }
        emitter.toolCall(
            callRef = ProviderToolCallRef(callId),
            ordinal = call.ordinal,
            name = name,
            argumentsJson = arguments,
        )
        call.emitted = true
    }

    private fun mergeCompletedResponse(response: JSONObject) {
        val output = response.optJSONArray("output") ?: return
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            if (item.optString("type", "") != "function_call") {
                continue
            }
            val call = functionCalls.getOrPut(index) { PendingFunctionCall(index) }
            call.mergeMetadata(
                callId = item.optString("call_id", ""),
                name = item.optString("name", ""),
            )
            call.mergeFinalArguments(item.optString("arguments", ""))
        }
    }

    private suspend fun emitFinalOutputText(response: JSONObject) {
        if (emittedTextLength > 0) {
            return
        }
        val output = response.optJSONArray("output") ?: return
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            if (item.optString("type", "") != "message") {
                continue
            }
            val content = item.optJSONArray("content") ?: continue
            for (partIndex in 0 until content.length()) {
                val part = content.optJSONObject(partIndex) ?: continue
                if (part.optString("type", "") in setOf("output_text", "text")) {
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) {
                        emitter.text(text)
                        emittedTextLength += text.length
                    }
                }
            }
        }
    }

    private suspend fun emitFinalReasoning(response: JSONObject) {
        if (emittedReasoningLength > 0) {
            return
        }
        val output = response.optJSONArray("output") ?: return
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            if (item.optString("type", "") != "reasoning") {
                continue
            }
            val summary = item.optJSONArray("summary") ?: continue
            for (partIndex in 0 until summary.length()) {
                val text = summary.optJSONObject(partIndex)?.optString("text", "") ?: ""
                if (text.isNotEmpty()) {
                    emitter.reasoning(text)
                    emittedReasoningLength += text.length
                }
            }
        }
    }

    private fun requireResponse(event: JSONObject): JSONObject {
        return event.optJSONObject("response")
            ?: throw OpenAiResponsesProtocolException("OpenAI Responses completed event has no response")
    }

    private fun providerError(event: JSONObject): AgentModelError {
        val error =
            event.optJSONObject("error")
                ?: event.optJSONObject("response")?.optJSONObject("error")
                ?: event
        val providerCode = error.optString("code", "")
        val modelErrorCode =
            when (providerCode) {
                "invalid_api_key", "authentication_error" -> AgentModelErrorCode.AUTHENTICATION
                "rate_limit_exceeded" -> AgentModelErrorCode.RATE_LIMIT
                "invalid_request_error" -> AgentModelErrorCode.INVALID_REQUEST
                else -> AgentModelErrorCode.PROVIDER
            }
        val message = error.optString("message", "").trim()
        if (message.isBlank()) {
            throw OpenAiResponsesProtocolException("OpenAI Responses error event has no message")
        }
        return AgentModelError(
            code = modelErrorCode,
            message = message.take(512),
            retryable = modelErrorCode == AgentModelErrorCode.RATE_LIMIT || modelErrorCode == AgentModelErrorCode.PROVIDER,
        )
    }
}

private class PendingFunctionCall(
    val ordinal: Int,
) {
    var callId: String? = null
        private set
    var name: String? = null
        private set
    var arguments: String = ""
        private set
    var emitted: Boolean = false

    fun mergeMetadata(callId: String, name: String) {
        if (callId.isNotBlank()) {
            this.callId = mergeRequiredValue(this.callId, callId, "call ID")
        }
        if (name.isNotBlank()) {
            this.name = mergeRequiredValue(this.name, name, "name")
        }
    }

    fun mergeDelta(delta: String) {
        if (delta.isEmpty()) {
            return
        }
        arguments =
            when {
                arguments.isEmpty() -> delta
                delta.startsWith(arguments) -> delta
                arguments.startsWith(delta) -> arguments
                else -> arguments + delta
            }
    }

    fun mergeFinalArguments(finalArguments: String) {
        if (finalArguments.isEmpty()) {
            return
        }
        arguments = finalArguments
    }

    private fun mergeRequiredValue(current: String?, incoming: String, label: String): String {
        if (current == null || current == incoming) {
            return incoming
        }
        throw OpenAiResponsesProtocolException("OpenAI Responses function call $label changed during one request")
    }
}

private class OpenAiResponsesProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
