package com.ai.assistance.operit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeToolCallMetadataTest {
    @Test
    fun `native call metadata round trips raw arguments and rounds`() {
        val encoded =
            NativeToolCallMetadataCodec.encode(
                toolCalls =
                    listOf(
                        NativeToolCall(
                            callId = "call_1",
                            toolName = "read_file",
                            argumentsJson = "{\"path\":\"/tmp/a\",\"line\":3}",
                            index = 0,
                            roundIndex = 2
                        )
                    ),
                toolResults =
                    listOf(
                        NativeToolResult(
                            callId = "call_1",
                            toolName = "read_file",
                            output = "content",
                            success = true,
                            roundIndex = 2
                        )
                    )
            )

        val decoded = NativeToolCallMetadataCodec.decode(encoded)!!

        assertEquals("{\"path\":\"/tmp/a\",\"line\":3}", decoded.toolCalls.single().argumentsJson)
        assertEquals(2, decoded.toolCalls.single().roundIndex)
        assertEquals("call_1", decoded.toolResults.single().callId)
        assertEquals(2, decoded.toolResults.single().roundIndex)
    }
}
