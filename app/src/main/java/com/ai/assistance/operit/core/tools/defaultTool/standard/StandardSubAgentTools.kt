package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.subagent.SubAgentManager
import com.ai.assistance.operit.api.chat.subagent.SubAgentTask
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.json.Json
import org.json.JSONArray

/**
 * 子代理工具实现
 */
class StandardSubAgentTools(private val context: Context) {

    companion object {
        private const val TAG = "StandardSubAgentTools"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val subAgentManager = SubAgentManager.getInstance(context)

    /**
     * 创建单个子代理执行任务
     */
    suspend fun spawnSubAgent(tool: AITool): ToolResult {
        val task = tool.parameters.find { it.name == "task" }?.value
        if (task.isNullOrBlank()) {
            return buildError(tool, "Missing required parameter: task")
        }

        val toolsWhitelist = parseToolsWhitelist(
            tool.parameters.find { it.name == "tools_whitelist" }?.value
        )
        val maxRounds = tool.parameters.find { it.name == "max_rounds" }?.value?.toIntOrNull() ?: 10
        val modelConfigId = tool.parameters.find { it.name == "model_config_id" }?.value

        // 需要从工具参数或上下文获取 parentChatId 和 parentMessageTimestamp
        val parentChatId = tool.parameters.find { it.name == "__operit_package_chat_id" }?.value
            ?: "default"
        val parentMessageTimestamp = System.currentTimeMillis()
        val generationId = subAgentManager.getNextGenerationId(parentChatId)

        AppLogger.i(TAG, "spawn_subagent: task='$task', gen=$generationId, maxRounds=$maxRounds")

        return try {
            val result = subAgentManager.spawnAndExecute(
                parentChatId = parentChatId,
                parentMessageTimestamp = parentMessageTimestamp,
                generationId = generationId,
                task = task,
                toolsWhitelist = toolsWhitelist,
                modelConfigId = modelConfigId?.takeIf { it.isNotBlank() },
                maxRounds = maxRounds,
            )

            ToolResult(
                toolName = tool.name,
                success = result.success,
                result = StringResultData(result.toDisplayString()),
                error = if (!result.success) result.errorMessage else ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "spawn_subagent 失败", e)
            buildError(tool, "子代理执行失败: ${e.message}")
        }
    }

    /**
     * 并行创建多个子代理执行任务
     */
    suspend fun spawnSubAgentsParallel(tool: AITool): ToolResult {
        val tasksJson = tool.parameters.find { it.name == "tasks" }?.value
        if (tasksJson.isNullOrBlank()) {
            return buildError(tool, "Missing required parameter: tasks")
        }

        val tasks = try {
            parseTasksArray(tasksJson)
        } catch (e: Exception) {
            return buildError(tool, "Invalid tasks format: ${e.message}")
        }

        if (tasks.isEmpty()) {
            return buildError(tool, "tasks array is empty")
        }

        val parentChatId = tool.parameters.find { it.name == "__operit_package_chat_id" }?.value
            ?: "default"
        val parentMessageTimestamp = System.currentTimeMillis()
        val generationId = subAgentManager.getNextGenerationId(parentChatId)

        AppLogger.i(TAG, "spawn_subagents_parallel: ${tasks.size} tasks, gen=$generationId")

        return try {
            val startTime = System.currentTimeMillis()
            val results = subAgentManager.spawnAndExecuteParallel(
                parentChatId = parentChatId,
                parentMessageTimestamp = parentMessageTimestamp,
                generationId = generationId,
                tasks = tasks,
            )
            val totalElapsed = System.currentTimeMillis() - startTime
            val successCount = results.count { it.success }
            val failedCount = results.count { !it.success }

            val displayString = buildString {
                appendLine("并行子代理执行完成: $successCount/${results.size} 成功 (耗时${totalElapsed}ms)")
                results.forEach { r ->
                    val status = if (r.success) "✅" else "❌"
                    appendLine("  $status ${r.agentName}: ${r.resultSummary.take(80)}")
                }
            }

            ToolResult(
                toolName = tool.name,
                success = failedCount == 0,
                result = StringResultData(displayString),
                error = if (failedCount > 0) "$failedCount/${results.size} 个子代理失败" else ""
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "spawn_subagents_parallel 失败", e)
            buildError(tool, "并行子代理执行失败: ${e.message}")
        }
    }

    // ─── 辅助方法 ─────────────────────────────────────────

    private fun parseToolsWhitelist(value: String?): List<String>? {
        if (value.isNullOrBlank()) return null
        // "all" 表示使用全部工具
        if (value.trim().equals("all", ignoreCase = true)) return null
        return try {
            val arr = JSONArray(value)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            AppLogger.w(TAG, "解析 tools_whitelist 失败: $value")
            null
        }
    }

    private fun parseTasksArray(json: String): List<SubAgentTask> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SubAgentTask(
                task = obj.getString("task"),
                toolsWhitelist = obj.optJSONArray("tools_whitelist")?.let { arr2 ->
                    (0 until arr2.length()).map { arr2.getString(it) }
                },
                maxRounds = obj.optInt("max_rounds", 10),
                modelConfigId = obj.optString("model_config_id", "").takeIf { it.isNotBlank() },
            )
        }
    }

    private fun buildError(tool: AITool, error: String): ToolResult {
        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = error
        )
    }
}
