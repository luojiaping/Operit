package com.ai.assistance.operit.services.core

import com.ai.assistance.operit.data.model.ChatMessageLocatorPreview
import com.ai.assistance.operit.data.model.ChatMessage

internal const val DISPLAY_PAGE_TRIGGER_COUNT = 5
internal const val MAX_DISPLAY_PAGE_COUNT = 2

// 窗口容量封顶（正确性回归）：分页原本只按 user/summary 触发计数，AI 分段消息全部
// 落入最后一页——waifu 模式把一条回复拆成 K 条分段，长会话下显示窗口随分段数无界
// 膨胀，非惰性渲染的列表重组成本随之失控。这里让页内消息条数（含 AI 分段）达到
// 上限即闭合页面，窗口最多 2 页 × 上限条，渲染压力有界；代价是超长回合会更早出现
// "加载更早"入口，属于预期行为变化。
internal const val MAX_DISPLAY_PAGE_MESSAGE_COUNT = 50

internal data class DisplayPageRange(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val startTimestampInclusive: Long,
    val endTimestampInclusive: Long,
)

internal fun splitDisplayPages(
    messages: List<ChatMessage>,
    triggerMessagesPerPage: Int = DISPLAY_PAGE_TRIGGER_COUNT,
): List<List<ChatMessage>> {
    if (messages.isEmpty()) {
        return emptyList()
    }

    return resolveDisplayPageRanges(
        messages = messages,
        senderOf = ChatMessage::sender,
        timestampOf = ChatMessage::timestamp,
        triggerMessagesPerPage = triggerMessagesPerPage,
    ).map { range ->
        messages.subList(range.startIndex, range.endIndexExclusive)
    }
}

internal fun countDisplayPages(messages: List<ChatMessage>): Int = splitDisplayPages(messages).size

internal fun resolveDisplayPageRanges(
    messages: List<ChatMessageLocatorPreview>,
    triggerMessagesPerPage: Int = DISPLAY_PAGE_TRIGGER_COUNT,
): List<DisplayPageRange> =
    resolveDisplayPageRanges(
        messages = messages,
        senderOf = ChatMessageLocatorPreview::sender,
        timestampOf = ChatMessageLocatorPreview::timestamp,
        triggerMessagesPerPage = triggerMessagesPerPage,
    )

internal fun takeNewestDisplayPages(
    messages: List<ChatMessage>,
    pageCount: Int,
): List<ChatMessage> {
    if (messages.isEmpty() || pageCount <= 0) {
        return emptyList()
    }
    return splitDisplayPages(messages)
        .takeLast(pageCount)
        .flatten()
}

internal fun takeOldestDisplayPages(
    messages: List<ChatMessage>,
    pageCount: Int,
): List<ChatMessage> {
    if (messages.isEmpty() || pageCount <= 0) {
        return emptyList()
    }
    return splitDisplayPages(messages)
        .take(pageCount)
        .flatten()
}

private inline fun <T> resolveDisplayPageRanges(
    messages: List<T>,
    senderOf: (T) -> String,
    timestampOf: (T) -> Long,
    triggerMessagesPerPage: Int,
): List<DisplayPageRange> {
    if (messages.isEmpty()) {
        return emptyList()
    }

    val safeTriggerMessagesPerPage = triggerMessagesPerPage.coerceAtLeast(1)
    val pageStartIndicesNewestFirst = mutableListOf<Int>()
    var cursor = messages.lastIndex

    while (cursor >= 0) {
        var pageStartIndex = 0
        var triggerCountInCurrentPage = 0
        var pageMessageCount = 0
        var pageClosed = false

        while (cursor >= 0 && !pageClosed) {
            val message = messages[cursor]
            pageMessageCount += 1
            if (isDisplayPageTriggerMessage(senderOf(message))) {
                triggerCountInCurrentPage += 1

                if (senderOf(message) == "summary") {
                    pageStartIndex = cursor
                    cursor -= 1
                    pageClosed = true
                }

                if (!pageClosed && triggerCountInCurrentPage >= safeTriggerMessagesPerPage) {
                    pageStartIndex = cursor
                    cursor -= 1
                    pageClosed = true
                }
            }

            if (!pageClosed && pageMessageCount >= MAX_DISPLAY_PAGE_MESSAGE_COUNT) {
                pageStartIndex = cursor
                cursor -= 1
                pageClosed = true
            }

            if (!pageClosed) {
                cursor -= 1
            }
        }

        if (!pageClosed) {
            pageStartIndex = 0
            cursor = -1
        }

        pageStartIndicesNewestFirst += pageStartIndex
    }

    val pageStartIndicesOldestFirst = pageStartIndicesNewestFirst.reversed()
    return pageStartIndicesOldestFirst.mapIndexed { index, pageStartIndex ->
        val pageEndExclusive =
            pageStartIndicesOldestFirst.getOrNull(index + 1) ?: messages.size
        DisplayPageRange(
            startIndex = pageStartIndex,
            endIndexExclusive = pageEndExclusive,
            startTimestampInclusive = timestampOf(messages[pageStartIndex]),
            endTimestampInclusive = timestampOf(messages[pageEndExclusive - 1]),
        )
    }
}

private fun isDisplayPageTriggerMessage(sender: String): Boolean {
    return sender == "user" || sender == "summary"
}
