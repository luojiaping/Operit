package com.ai.assistance.operit.api.chat.subagent

import android.content.Context
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 智能子代理名称生成器
 *
 * 策略（按优先级）：
 * 1. 快速规则提取（零成本）
 * 2. LLM 摘要（异步，有成本但更智能）
 */
object AgentNameGenerator {

    private const val TAG = "AgentNameGenerator"
    private const val MAX_NAME_LENGTH = 8  // 最多8个中文字符

    // 噪音动词前缀（会被去掉）
    private val NOISE_VERBS = listOf(
        "帮我", "请", "请你", "麻烦", "能不能", "可不可以",
        "分析一下", "看一下", "查一下", "检查一下", "看看",
        "去", "来", "把", "将", "对", "给",
    )

    // 常见任务后缀词（会被去掉）
    private val NOISE_SUFFIXES = listOf(
        "一下", "看看", "吧", "呢", "吗", "哦", "了",
    )

    /**
     * 快速提取名称（纯规则，零成本，毫秒级）
     * @return 名称或 null（如果无法提取有意义的名称）
     */
    fun extractQuickName(task: String): String? {
        var cleaned = task.trim()
        if (cleaned.isEmpty()) return null

        // 如果很短，直接返回
        if (cleaned.length <= MAX_NAME_LENGTH) return cleaned

        // 去掉噪音动词前缀
        for (verb in NOISE_VERBS) {
            if (cleaned.startsWith(verb)) {
                cleaned = cleaned.removePrefix(verb).trim()
            }
        }

        // 去掉噪音后缀
        for (suffix in NOISE_SUFFIXES) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.removeSuffix(suffix).trim()
            }
        }

        // 如果清理后仍然太长，截断
        if (cleaned.length > MAX_NAME_LENGTH) {
            cleaned = cleaned.take(MAX_NAME_LENGTH)
        }

        return cleaned.ifEmpty { null }
    }

    /**
     * 使用 LLM 生成智能名称
     * 通过 SUMMARY 功能类型的 AIService，用一句话概括任务
     *
     * @param task 任务描述
     * @param context Android Context
     * @return 智能名称，失败时回退到快速提取
     */
    suspend fun generateSmartName(
        task: String,
        context: Context
    ): String {
        // 先用规则提取作为兜底
        val fallback = extractQuickName(task) ?: "子任务"

        return try {
            // 尝试用 LLM 生成（限时 5 秒）
            val llmResult = withTimeoutOrNull(5000L) {
                withContext(Dispatchers.IO) {
                    val service = com.ai.assistance.operit.api.chat.EnhancedAIService
                        .getAIServiceForFunction(context, FunctionType.SUMMARY)
                    val prompt = buildString {
                        appendLine("请用1-4个中文字概括以下任务的核心目标。")
                        appendLine("只输出名称，不要标点符号，不要解释。")
                        appendLine()
                        appendLine("任务：$task")
                    }
                    val result = StringBuilder()
                    service.sendMessage(
                        context = context,
                        chatHistory = listOf(
                            com.ai.assistance.operit.core.chat.hooks.PromptTurn(
                                kind = com.ai.assistance.operit.core.chat.hooks.PromptTurnKind.USER,
                                content = prompt
                            )
                        ),
                        modelParameters = emptyList(),
                        enableThinking = false,
                        stream = false,
                        availableTools = null
                    ).collect { chunk ->
                        result.append(chunk)
                    }
                    result.toString().trim()
                        .replace(Regex("[\"'.。!！?？,，:：;；\\s]"), "")  // 清理标点
                        .take(MAX_NAME_LENGTH)
                        .ifEmpty { null }
                }
            }

            llmResult ?: fallback
        } catch (e: Exception) {
            AppLogger.w(TAG, "LLM 名称生成失败，使用规则回退: ${e.message}")
            fallback
        }
    }
}