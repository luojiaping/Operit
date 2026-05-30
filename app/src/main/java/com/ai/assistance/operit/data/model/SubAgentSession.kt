package com.ai.assistance.operit.data.model

/**
 * 子代理会话数据类
 * 纯内存 + JSON 文件持久化（不依赖 ObjectBox）
 */
data class SubAgentSession(
    val agentId: String = "",                      // UUID
    val parentChatId: String = "",                 // 主对话 chatId
    val parentMessageTimestamp: Long = 0L,         // 触发该子代理的主代理消息时间戳
    val generationId: Int = 0,                     // 第几代
    var name: String = "",                         // 自动生成的名称
    val task: String = "",                         // 任务描述
    var status: String = SubAgentStatus.PENDING,   // 状态常量
    val createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0L,
    var resultSummary: String = "",                // 结果摘要
    var errorMessage: String = "",
    val toolsWhitelist: String = "",               // JSON 数组，空 = 默认白名单
    val modelConfigId: String = "",                // 空 = 继承主代理配置
    val maxRounds: Int = 10,
    var roundsUsed: Int = 0,
    val subChatId: String = "",                    // 子代理独立的 chatId
)

/**
 * 子代理状态枚举
 */
object SubAgentStatus {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}
