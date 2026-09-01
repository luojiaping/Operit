package com.ai.assistance.operit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaifuMessageProcessorTest {
    // 行前缀稳定性门控矩阵：开放尾段（尾部块未闭合）时块前缀行不得作为
    // "无句尾也可发射"的稳定依据，否则编号/列表/标题行被逐 chunk 切成
    // 1-2 字碎片段；URL/邮箱行豁免不受门控影响
    @Test
    fun lineStability_blockPrefixLineStableWhenBlockClosed() {
        assertTrue(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "1. 今天",
                allowBlockPrefixExemption = true
            )
        )
    }

    @Test
    fun lineStability_orderedListPrefixLineHeldWhenTailBlockOpen() {
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "1. 今天",
                allowBlockPrefixExemption = false
            )
        )
    }

    @Test
    fun lineStability_headerQuoteUnorderedPrefixLinesHeldWhenTailBlockOpen() {
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "## 标题正在",
                allowBlockPrefixExemption = false
            )
        )
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "> 引用正在",
                allowBlockPrefixExemption = false
            )
        )
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "- 列表正在",
                allowBlockPrefixExemption = false
            )
        )
    }

    @Test
    fun lineStability_codeTableLatexMarkersHeldWhenTailBlockOpen() {
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "```kotlin",
                allowBlockPrefixExemption = false
            )
        )
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "| 列1 |",
                allowBlockPrefixExemption = false
            )
        )
    }

    @Test
    fun lineStability_urlAndEmailLinesStillStableWhenTailBlockOpen() {
        assertTrue(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "👉 https://waifu.example.test/sheet/demo-link",
                allowBlockPrefixExemption = false
            )
        )
        assertTrue(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "✉️ 发送至：submit-team@waifu.example.test",
                allowBlockPrefixExemption = false
            )
        )
    }

    @Test
    fun lineStability_plainLineNeverStableWithoutEnding() {
        assertFalse(
            WaifuMessageProcessor.lineAllowsStableWithoutSentenceEnding(
                line = "今天天气",
                allowBlockPrefixExemption = true
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_firstSegmentIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = true,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_usesCurrentSegmentLengthForShortTail() {
        assertEquals(
            720L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 3,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_capsLongSegmentDelay() {
        assertEquals(
            3000L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 80,
                charDelayMs = 240,
                isFirstSegment = false,
            )
        )
    }

    @Test
    fun calculateTypingDelayMs_nonPositiveDelayIsImmediate() {
        assertEquals(
            0L,
            WaifuMessageProcessor.calculateTypingDelayMs(
                segmentLength = 10,
                charDelayMs = 0,
                isFirstSegment = false,
            )
        )
    }
}
