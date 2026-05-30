package com.ai.assistance.operit.api.chat.subagent

/**
 * 子代理工具的返回值数据类
 * 注意：由于 ToolResultData 是 sealed class，子类必须在同包。
 *       这里用独立的 data class，由 StandardSubAgentTools 在 ToolResult 中
 *       通过 StringResultData 包装返回。
 */
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
