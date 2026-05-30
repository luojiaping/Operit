package com.ai.assistance.operit.api.chat.subagent

import com.ai.assistance.operit.core.tools.ToolResultData
import kotlinx.serialization.Serializable

/**
 * 子代理工具的返回值数据类
 */
@Serializable
data class SubAgentResultData(
    val agentId: String,
    val agentName: String,
    val task: String,
    val success: Boolean,
    val resultSummary: String,
    val errorMessage: String = "",
    val elapsedTimeMs: Long = 0,
    val roundsUsed: Int = 0,
    val subChatId: String = "",
) : ToolResultData() {
    override fun toString(): String {
        return if (success) {
            "[$agentName] 完成: $resultSummary (耗时${elapsedTimeMs}ms, ${roundsUsed}轮)"
        } else {
            "[$agentName] 失败: $errorMessage"
        }
    }
}

/**
 * 多子代理并行执行的聚合返回值
 */
@Serializable
data class SubAgentsParallelResultData(
    val results: List<SubAgentResultData>,
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalElapsedMs: Long,
) : ToolResultData() {
    override fun toString(): String {
        return buildString {
            appendLine("并行子代理执行完成: $successCount/$totalCount 成功")
            results.forEach { r ->
                val status = if (r.success) "✅" else "❌"
                appendLine("  $status ${r.agentName}: ${r.resultSummary.take(80)}")
            }
        }
    }
}