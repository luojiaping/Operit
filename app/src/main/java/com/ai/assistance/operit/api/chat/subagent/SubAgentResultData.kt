package com.ai.assistance.operit.api.chat.subagent

import kotlinx.serialization.Serializable

/**
 * 子代理工具的返回值数据类
 * 注意：由于 ToolResultData 是 sealed class，子类必须在同包。
 *       这里用独立的 @Serializable data class，由 StandardSubAgentTools 在 ToolResult 中
 *       通过 StringResultData 包装返回。
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
) {
    fun toDisplayString(): String {
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
) {
    fun toDisplayString(): String {
        return buildString {
            appendLine("并行子代理执行完成: $successCount/$totalCount 成功")
            results.forEach { r ->
                val status = if (r.success) "✅" else "❌"
                appendLine("  $status ${r.agentName}: ${r.resultSummary.take(80)}")
            }
        }
    }
}