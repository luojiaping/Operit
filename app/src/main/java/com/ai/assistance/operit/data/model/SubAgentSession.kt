package com.ai.assistance.operit.data.model

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * 子代理会话实体 (ObjectBox)
 * 每个子代理任务对应一条记录，数据永久保留。
 */
@Entity
data class SubAgentSession(
    @Id var id: Long = 0,
    @Index var agentId: String = "",                // UUID
    @Index var parentChatId: String = "",           // 主对话 chatId
    var parentMessageTimestamp: Long = 0L,          // 触发该子代理的主代理消息时间戳
    @Index var generationId: Int = 0,               // 第几代
    var name: String = "",                          // 自动生成的名称
    var task: String = "",                          // 任务描述
    var status: String = SubAgentStatus.PENDING,    // SubAgentStatus 枚举值
    var createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0L,
    var resultSummary: String = "",                 // 结果摘要
    var errorMessage: String = "",
    var toolsWhitelist: String = "",                // JSON 数组，空 = 默认白名单
    var modelConfigId: String = "",                 // 空 = 继承主代理
    var maxRounds: Int = 10,
    var roundsUsed: Int = 0,                        // 实际使用了多少轮
    var subChatId: String = "",                     // 子代理独立的 chatId
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
