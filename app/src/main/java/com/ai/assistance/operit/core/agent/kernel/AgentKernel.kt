package com.ai.assistance.operit.core.agent.kernel

import com.ai.assistance.operit.core.agent.history.AgentHistoryItem
import com.ai.assistance.operit.core.agent.history.AgentHistoryItemKind
import com.ai.assistance.operit.core.agent.model.AgentModelClient
import com.ai.assistance.operit.core.agent.model.AgentModelEvent
import com.ai.assistance.operit.core.agent.model.AgentModelMessage
import com.ai.assistance.operit.core.agent.model.AgentModelMessageRole
import com.ai.assistance.operit.core.agent.model.AgentModelRequest
import com.ai.assistance.operit.core.agent.model.AgentModelStopReason
import com.ai.assistance.operit.core.agent.model.AgentModelUsage
import com.ai.assistance.operit.data.model.ChatMessageTimestampAllocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AgentKernel(
    private val store: AgentKernelStore,
    private val modelClient: AgentModelClient,
    private val clock: AgentKernelClock = AgentKernelClock { ChatMessageTimestampAllocator.next() },
) {
    suspend fun recoverInterruptedRuns(): Int {
        return store.recoverInterruptedRuns(clock.now())
    }

    fun execute(command: AgentKernelCommand): Flow<AgentKernelEvent> =
        channelFlow {
            var reservation: AgentRunReservation? = null
            var settled = false
            var kernelSequence = 0L
            val text = StringBuilder()
            val reasoning = StringBuilder()
            var usage: AgentModelUsage? = null
            try {
                val reserved =
                    withContext(NonCancellable) {
                        val result =
                            store.reserveRun(
                                AgentRunReserveRequest(
                                    command = command,
                                    now = clock.now(),
                                )
                            )
                        reservation = result
                        result
                    }
                currentCoroutineContext().ensureActive()
                send(
                    AgentKernelEvent.RunStarted(
                        sessionId = command.sessionId,
                        runId = command.runId,
                        sequence = kernelSequence++,
                        occurredAt = clock.now(),
                        stepId = command.stepId,
                    )
                )

                val modelRequest =
                    AgentModelRequest(
                        modelRequestId = command.modelRequestId,
                        sessionId = command.sessionId,
                        runId = command.runId,
                        stepId = command.stepId,
                        modelSnapshotJson = command.modelSnapshotJson,
                        systemPrompt = command.promptSnapshot,
                        history = reserved.history.map(::toModelMessage),
                        toolSnapshotJson = command.toolSnapshotJson,
                    )
                var expectedEventIndex = 0L

                val terminalEvent =
                    try {
                        modelClient.execute(modelRequest).collect { event ->
                            ensureModelEventIdentity(event, modelRequest, expectedEventIndex)
                            expectedEventIndex += 1L
                            when (event) {
                                is AgentModelEvent.TextDelta -> {
                                    requireModelProtocol(
                                        event.text.isNotEmpty(),
                                        "Agent model emitted an empty text delta",
                                    )
                                    text.append(event.text)
                                    send(
                                        AgentKernelEvent.AssistantTextDelta(
                                            sessionId = command.sessionId,
                                            runId = command.runId,
                                            sequence = kernelSequence++,
                                            occurredAt = clock.now(),
                                            text = event.text,
                                        )
                                    )
                                }

                                is AgentModelEvent.ReasoningDelta -> {
                                    requireModelProtocol(
                                        event.text.isNotEmpty(),
                                        "Agent model emitted an empty reasoning delta",
                                    )
                                    reasoning.append(event.text)
                                    send(
                                        AgentKernelEvent.AssistantReasoningDelta(
                                            sessionId = command.sessionId,
                                            runId = command.runId,
                                            sequence = kernelSequence++,
                                            occurredAt = clock.now(),
                                            text = event.text,
                                        )
                                    )
                                }

                                is AgentModelEvent.Usage -> {
                                    requireModelProtocol(
                                        usage == null,
                                        "Agent model emitted usage more than once",
                                    )
                                    usage = event.usage
                                    send(
                                        AgentKernelEvent.UsageReported(
                                            sessionId = command.sessionId,
                                            runId = command.runId,
                                            sequence = kernelSequence++,
                                            occurredAt = clock.now(),
                                            usage = event.usage,
                                        )
                                    )
                                }

                                is AgentModelEvent.ToolCallReady ->
                                    throw AgentKernelExecutionException(
                                        AgentKernelError(
                                            code = AgentKernelErrorCode.TOOLS_NOT_ENABLED,
                                            message = "Text-only AgentKernel does not execute tool calls",
                                        )
                                    )

                                is AgentModelEvent.Completed,
                                is AgentModelEvent.Failed -> throw AgentModelTerminalSignal(event)
                            }
                        }
                        null
                    } catch (terminal: AgentModelTerminalSignal) {
                        terminal.event
                    }

                val terminal =
                    terminalEvent
                        ?: throw AgentKernelExecutionException(
                            AgentKernelError(
                                code = AgentKernelErrorCode.MODEL_PROTOCOL,
                                message = "Agent model event stream ended without a terminal event",
                            )
                        )
                when (terminal) {
                    is AgentModelEvent.Completed -> {
                        if (terminal.stopReason == AgentModelStopReason.TOOL_CALL) {
                            throw AgentKernelExecutionException(
                                AgentKernelError(
                                    code = AgentKernelErrorCode.TOOLS_NOT_ENABLED,
                                    message = "Text-only AgentKernel received a tool-call stop reason",
                                )
                            )
                        }
                        if (text.isBlank()) {
                            throw AgentKernelExecutionException(
                                AgentKernelError(
                                    code = AgentKernelErrorCode.EMPTY_RESPONSE,
                                    message = "Agent model completed without assistant text",
                                )
                            )
                        }
                        val completed =
                            withContext(NonCancellable) {
                                val result =
                                    store.completeRun(
                                        AgentRunCompleteRequest(
                                            reservation = reserved,
                                            assistantText = text.toString(),
                                            reasoningText = reasoning.toString(),
                                            usageJson = usage?.let(::encodeUsage),
                                            usage = usage,
                                            finishReason = terminal.stopReason,
                                            assistantTimestamp = clock.now(),
                                            now = clock.now(),
                                        )
                                    )
                                settled = true
                                result
                            }
                        currentCoroutineContext().ensureActive()
                        val outputMessage = requireNotNull(completed.outputMessage) {
                            "Completed Agent run did not return an output message"
                        }
                        send(
                            AgentKernelEvent.AssistantMessageCommitted(
                                sessionId = command.sessionId,
                                runId = command.runId,
                                sequence = kernelSequence++,
                                occurredAt = clock.now(),
                                message = outputMessage,
                            )
                        )
                        send(
                            AgentKernelEvent.RunCompleted(
                                sessionId = command.sessionId,
                                runId = command.runId,
                                sequence = kernelSequence,
                                occurredAt = clock.now(),
                                finishReason = terminal.stopReason,
                            )
                        )
                    }

                    is AgentModelEvent.Failed -> {
                        val error =
                            AgentKernelError(
                                code = AgentKernelErrorCode.MODEL_FAILED,
                                message = "Agent model failed with ${terminal.error.code.name}",
                            )
                        withContext(NonCancellable) {
                            store.failRun(
                                AgentRunFailRequest(
                                    reservation = reserved,
                                    errorCode = "MODEL_${terminal.error.code.name}",
                                    errorMessage = terminal.error.message,
                                    assistantText = text.toString(),
                                    reasoningText = reasoning.toString(),
                                    usageJson = usage?.let(::encodeUsage),
                                    now = clock.now(),
                                )
                            )
                            settled = true
                        }
                        currentCoroutineContext().ensureActive()
                        send(
                            AgentKernelEvent.RunFailed(
                                sessionId = command.sessionId,
                                runId = command.runId,
                                sequence = kernelSequence,
                                occurredAt = clock.now(),
                                error = error,
                            )
                        )
                    }

                    else -> error("Non-terminal Agent model event reached terminal settlement")
                }
            } catch (cancellation: CancellationException) {
                val activeReservation = reservation
                if (activeReservation != null && !settled) {
                    withContext(NonCancellable) {
                        store.cancelRun(
                            reservation = activeReservation,
                            assistantText = text.toString(),
                            reasoningText = reasoning.toString(),
                            usageJson = usage?.let(::encodeUsage),
                            now = clock.now(),
                        )
                    }
                }
                throw cancellation
            } catch (throwable: Throwable) {
                val activeReservation = reservation ?: throw throwable
                if (settled) {
                    throw throwable
                }
                val error =
                    when (throwable) {
                        is AgentKernelExecutionException -> throwable.error
                        else ->
                            AgentKernelError(
                                code = AgentKernelErrorCode.MODEL_STREAM,
                                message = "Agent model event stream threw ${throwable::class.java.name}",
                            )
                    }
                withContext(NonCancellable) {
                    store.failRun(
                        AgentRunFailRequest(
                            reservation = activeReservation,
                            errorCode = error.code.name,
                            errorMessage = error.message,
                            assistantText = text.toString(),
                            reasoningText = reasoning.toString(),
                            usageJson = usage?.let(::encodeUsage),
                            now = clock.now(),
                        )
                    )
                    settled = true
                }
                currentCoroutineContext().ensureActive()
                send(
                    AgentKernelEvent.RunFailed(
                        sessionId = command.sessionId,
                        runId = command.runId,
                        sequence = kernelSequence,
                        occurredAt = clock.now(),
                        error = error,
                    )
                )
            }
        }

    private fun ensureModelEventIdentity(
        event: AgentModelEvent,
        request: AgentModelRequest,
        expectedEventIndex: Long,
    ) {
        requireModelProtocol(
            event.modelRequestId == request.modelRequestId,
            "Agent model event belongs to another model request",
        )
        requireModelProtocol(
            event.eventIndex == expectedEventIndex,
            "Agent model event index ${event.eventIndex} does not match $expectedEventIndex",
        )
    }

    private fun toModelMessage(item: AgentHistoryItem): AgentModelMessage {
        val role =
            when (item.kind) {
                AgentHistoryItemKind.USER -> AgentModelMessageRole.USER
                AgentHistoryItemKind.ASSISTANT,
                AgentHistoryItemKind.SUMMARY -> AgentModelMessageRole.ASSISTANT
                AgentHistoryItemKind.TOOL_CALL,
                AgentHistoryItemKind.TOOL_RESULT,
                AgentHistoryItemKind.COMPACTION ->
                    throw AgentKernelExecutionException(
                        AgentKernelError(
                            code = AgentKernelErrorCode.TOOLS_NOT_ENABLED,
                            message = "Text-only AgentKernel history contains ${item.kind.name}",
                        )
                    )
            }
        return AgentModelMessage(role = role, content = item.content)
    }

    private fun encodeUsage(usage: AgentModelUsage): String {
        return buildJsonObject {
            put("inputTokens", usage.inputTokens)
            put("cachedInputTokens", usage.cachedInputTokens)
            put("outputTokens", usage.outputTokens)
        }.toString()
    }

    private fun requireModelProtocol(condition: Boolean, message: String) {
        if (!condition) {
            throw AgentKernelExecutionException(
                AgentKernelError(
                    code = AgentKernelErrorCode.MODEL_PROTOCOL,
                    message = message,
                )
            )
        }
    }
}

private class AgentKernelExecutionException(
    val error: AgentKernelError,
) : IllegalStateException(error.message)

private class AgentModelTerminalSignal(
    val event: AgentModelEvent,
) : CancellationException("Agent model terminal event received")
