package com.ai.assistance.operit.util.stream

import com.ai.assistance.operit.data.model.NativeToolCall
import org.junit.Assert.assertEquals
import org.junit.Test

class TextStreamEventTest {
    @Test
    fun `native tool event carries the provider call`() {
        val call =
            NativeToolCall(
                callId = "call_1",
                toolName = "read_file",
                argumentsJson = "{\"path\":\"/tmp/a\"}",
                roundIndex = 3
            )
        val event =
            TextStreamEvent(
                eventType = TextStreamEventType.NATIVE_TOOL_CALL,
                id = call.callId,
                nativeToolCall = call
            )

        assertEquals(call, event.nativeToolCall)
        assertEquals(TextStreamEventType.NATIVE_TOOL_CALL, event.eventType)
    }
}
