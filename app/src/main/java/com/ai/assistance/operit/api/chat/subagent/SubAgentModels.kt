package com.ai.assistance.operit.api.chat.subagent

import com.ai.assistance.operit.data.model.SubAgentStatus

/**
 * 子代理卡片的实时 UI 状态（不持久化，运行时由 SubAgentManager 通过 StateFlow 推送）
 */
data class SubAgentCardState(
    val agentId: String,
    val name: String,
    val task: String,
    val status: String,              // SubAgentStatus 常量
    val elapsedTimeMs: Long = 0,
    val resultSummary: String? = null,
    val currentStep: String? = null, // 当前正在执行的步骤描述（如 "正在读取文件..."）
    val subChatId: String = "",      // 子代理的独立 chatId
    val roundsUsed: Int = 0,
    val maxRounds: Int = 10,
)

/**
 * 一个 generation 代表主代理发出的同一批子代理
 */
data class SubAgentGeneration(
    val generationId: Int,
    val parentChatId: String,
    val parentMessageTimestamp: Long,
    val agents: List<SubAgentCardState>,
) {
    val allCompleted: Boolean get() = agents.all {
        it.status == SubAgentStatus.COMPLETED ||
            it.status == SubAgentStatus.FAILED ||
            it.status == SubAgentStatus.CANCELLED
    }

    val anyRunning: Boolean get() = agents.any { it.status == SubAgentStatus.RUNNING }
    val completedCount: Int get() = agents.count { it.status == SubAgentStatus.COMPLETED }
    val failedCount: Int get() = agents.count { it.status == SubAgentStatus.FAILED }
}

/**
 * 传入 spawn_subagents_parallel 的单个任务定义
 */
data class SubAgentTask(
    val task: String,
    val toolsWhitelist: List<String>? = null,
    val maxRounds: Int = 10,
    val modelConfigId: String? = null,
)