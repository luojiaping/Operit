package com.ai.assistance.operit.util

import com.ai.assistance.operit.data.model.NativeToolCall
import com.ai.assistance.operit.data.model.NativeToolCallMetadata
import com.ai.assistance.operit.data.model.NativeToolResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolCallDisplayMarkupTest {
    @Test
    fun `display markup restores native tool names before each result round`() {
        val metadata =
            NativeToolCallMetadata(
                toolCalls =
                    listOf(
                        NativeToolCall("call_1", "read_file", "{\"path\":\"/tmp/a\"}", roundIndex = 0),
                        NativeToolCall("call_2", "list_files", "{\"path\":\"/tmp\"}", roundIndex = 1)
                    ),
                toolResults =
                    listOf(
                        NativeToolResult(
                            "call_1", "read_file", "content", true, roundIndex = 0
                        ),
                        NativeToolResult(
                            "call_2", "list_files", "files", true, roundIndex = 1
                        )
                    )
            )
        val content =
            "<tool_result name=\"read_file\" status=\"success\"><content>content</content></tool_result>" +
                "<tool_result name=\"list_files\" status=\"success\"><content>files</content></tool_result>"

        val rendered = buildNativeToolCallDisplayContent(content, metadata)

        assertTrue(rendered.indexOf("name=\"read_file\"") < rendered.indexOf("name=\"list_files\""))
        assertTrue(rendered.indexOf("<tool name=\"read_file\"") < rendered.indexOf("<tool_result"))
        assertTrue(rendered.indexOf("<tool name=\"list_files\"") > rendered.indexOf("</tool_result>"))
    }

    @Test
    fun `display markup is not duplicated when content already has a tool block`() {
        val metadata =
            NativeToolCallMetadata(
                toolCalls = listOf(NativeToolCall("call_1", "read_file", "{}"))
            )
        val content = "<tool name=\"read_file\"><param name=\"path\">/tmp/a</param></tool>"

        val rendered = buildNativeToolCallDisplayContent(content, metadata)

        assertEquals(content, rendered)
    }
}
