package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.data.model.NativeToolCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolInvocationTest {
    @Test
    fun `native invocation keeps structured call and decodes primitive arguments`() {
        val nativeCall =
            NativeToolCall(
                callId = "call_42",
                toolName = "calculate",
                argumentsJson = "{\"expression\":\"1+1\",\"round\":2,\"trace\":true}"
            )

        val invocation = ToolExecutionManager.createNativeToolInvocation(nativeCall)

        assertEquals(nativeCall, invocation.nativeToolCall)
        assertEquals("calculate", invocation.tool.name)
        assertEquals("1+1", invocation.tool.parameters.first { it.name == "expression" }.value)
        assertEquals("2", invocation.tool.parameters.first { it.name == "round" }.value)
        assertEquals("true", invocation.tool.parameters.first { it.name == "trace" }.value)
        assertTrue(invocation.rawText.isEmpty())
    }
}
