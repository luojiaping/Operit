package com.ai.assistance.operit.api.chat.enhance

import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.ChatMarkupRegex
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMarkupManagerTest {
    @Test
    fun `native tool result markup retains call id`() {
        val markup =
            ConversationMarkupManager.formatToolResultForMessage(
                ToolResult(
                    toolName = "read_file",
                    success = true,
                    result = StringResultData("content"),
                    toolCallId = "call_123"
                )
            )

        assertEquals("call_123", ChatMarkupRegex.toolCallIdAttr.find(markup)?.groupValues?.get(1))
    }
}
