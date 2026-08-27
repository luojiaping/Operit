package com.ai.assistance.operit.util

import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.NativeToolCall
import com.ai.assistance.operit.data.model.NativeToolCallMetadata
import com.ai.assistance.operit.data.model.NativeToolCallMetadataCodec
import org.json.JSONObject

private const val TAG = "NativeToolCallDisplayMarkup"

/** Builds the existing tool-row markup for display only; provider history remains structured. */
internal fun NativeToolCall.toDisplayMarkup(): String? {
    return try {
        val arguments = JSONObject(argumentsJson)
        buildString {
            append("\n<tool name=\"")
            append(escapeXmlAttribute(toolName))
            append("\" call_id=\"")
            append(escapeXmlAttribute(callId))
            append("\">")

            val keys = arguments.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                append("\n<param name=\"")
                append(escapeXmlAttribute(key))
                append("\">")
                append(escapeXmlText(arguments.opt(key).toString()))
                append("</param>")
            }
            append("\n</tool>\n")
        }
    } catch (e: Exception) {
        AppLogger.e(
            TAG,
            "原生工具调用展示参数解析失败: tool=$toolName, call_id=$callId",
            e
        )
        null
    }
}

internal fun List<NativeToolCall>.toDisplayMarkup(): String {
    return mapNotNull { it.toDisplayMarkup() }.joinToString(separator = "")
}

/** Reconstructs the old tool-row appearance from persisted native metadata. */
internal fun buildNativeToolCallDisplayContent(
    content: String,
    metadata: NativeToolCallMetadata
): String {
    if (metadata.toolCalls.isEmpty() || ChatMarkupRegex.containsToolTag(content)) {
        return content
    }

    val resultMatches = ChatMarkupRegex.toolResultAnyPattern.findAll(content).toList()
    val callsByRound = metadata.toolCalls.groupBy { it.roundIndex }.toSortedMap()
    val resultsByRound = metadata.toolResults.groupBy { it.roundIndex }.toSortedMap()
    val insertions = mutableListOf<Pair<Int, String>>()
    var resultCursor = 0

    callsByRound.forEach { (roundIndex, calls) ->
        val resultCount = resultsByRound[roundIndex]?.size ?: 0
        val insertionIndex =
            if (resultCount > 0) {
                resultMatches.getOrNull(resultCursor)?.range?.first ?: content.length
            } else {
                content.length
            }
        val markup = calls.toDisplayMarkup()
        if (markup.isNotEmpty()) {
            insertions.add(insertionIndex to markup)
        }
        resultCursor += resultCount
    }

    if (insertions.isEmpty()) {
        return content
    }

    val rendered = StringBuilder(content)
    insertions
        .sortedByDescending { it.first }
        .forEach { (index, markup) -> rendered.insert(index, markup) }
    return rendered.toString()
}

internal fun ChatMessage.nativeToolCallDisplayContent(): String {
    val metadata = NativeToolCallMetadataCodec.decode(toolCallMetadataJson)
        ?: return content
    return buildNativeToolCallDisplayContent(content, metadata)
}

private fun escapeXmlAttribute(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun escapeXmlText(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
