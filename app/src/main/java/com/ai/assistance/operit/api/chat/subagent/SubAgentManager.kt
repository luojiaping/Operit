package com.ai.assistance.operit.api.chat.subagent

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.EnhancedAIService.SendMessageOptions
import com.ai.assistance.operit.data.model.SubAgentSession
import com.ai.assistance.operit.data.model.SubAgentStatus
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 子代理管理器
 * 负责创建、执行、查询、取消子代理，通过 StateFlow 向 UI 层暴露实时状态。
 */
class SubAgentManager(private val context: Context) {

    companion object {
        private const val TAG = "SubAgentManager"

        @Volatile
        private var instance: SubAgentManager? = null

        fun getInstance(context: Context): SubAgentManager {
            return instance ?: synchronized(this) {
                instance ?: SubAgentManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 当前活跃的子代理状态（agentId -> CardState），供 UI 实时订阅
    private val _activeStates = MutableStateFlow<Map<String, SubAgentCardState>>(emptyMap())
    val activeStates: StateFlow<Map<String, SubAgentCardState>> = _activeStates.asStateFlow()

    // 子代理启动时间追踪（agentId -> 启动时间戳）
    private val agentStartTimes = ConcurrentHashMap<String, Long>()

    // 内存中的会话列表（全部会话，永久保留直到应用进程结束）
    private val allSessions = mutableListOf<SubAgentSession>()

    // ─── 工具执行上下文（ThreadLocal，用于传递 chatId 给工具） ───

    data class ToolExecutionContext(
        val parentChatId: String,
        val parentMessageTimestamp: Long
    )

    private val currentToolContext = ThreadLocal<ToolExecutionContext?>()

    /**
     * 在 EnhancedAIService.handleToolInvocation 中调用，
     * 设置当前工具执行的上下文信息
     */
    fun setToolExecutionContext(parentChatId: String, parentMessageTimestamp: Long) {
        currentToolContext.set(ToolExecutionContext(parentChatId, parentMessageTimestamp))
    }

    /**
     * 获取当前工具执行的上下文（由 StandardSubAgentTools 调用）
     */
    fun getToolExecutionContext(): ToolExecutionContext? = currentToolContext.get()

    /**
     * 清除工具执行上下文
     */
    fun clearToolExecutionContext() {
        currentToolContext.remove()
    }

    // ─── 持久化（JSON 文件） ──────────────────────────────────

    private val storeDir: File by lazy {
        File(context.filesDir, "subagent_sessions").also { it.mkdirs() }
    }

    private fun saveSession(session: SubAgentSession) {
        synchronized(allSessions) {
            val existing = allSessions.indexOfFirst { it.agentId == session.agentId }
            if (existing >= 0) allSessions[existing] = session else allSessions.add(session)
        }
        // 异步写入文件（不阻塞主线程）
        try {
            val file = File(storeDir, "${session.agentId}.json")
            file.writeText(sessionToJson(session).toString())
        } catch (e: Exception) {
            AppLogger.w(TAG, "保存子代理会话文件失败: ${session.agentId}", e)
        }
    }

    private fun loadSessionsFromDisk(): List<SubAgentSession> {
        val sessions = mutableListOf<SubAgentSession>()
        try {
            storeDir.listFiles()?.forEach { file ->
                if (file.extension == "json") {
                    try {
                        sessions.add(jsonToSession(JSONObject(file.readText())))
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "读取子代理会话文件失败: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "加载子代理会话目录失败", e)
        }
        return sessions
    }

    private fun sessionToJson(s: SubAgentSession): JSONObject = JSONObject().apply {
        put("agentId", s.agentId)
        put("parentChatId", s.parentChatId)
        put("parentMessageTimestamp", s.parentMessageTimestamp)
        put("generationId", s.generationId)
        put("name", s.name)
        put("task", s.task)
        put("status", s.status)
        put("createdAt", s.createdAt)
        put("completedAt", s.completedAt)
        put("resultSummary", s.resultSummary)
        put("errorMessage", s.errorMessage)
        put("toolsWhitelist", s.toolsWhitelist)
        put("modelConfigId", s.modelConfigId)
        put("maxRounds", s.maxRounds)
        put("roundsUsed", s.roundsUsed)
        put("subChatId", s.subChatId)
    }

    private fun jsonToSession(j: JSONObject): SubAgentSession = SubAgentSession(
        agentId = j.optString("agentId", ""),
        parentChatId = j.optString("parentChatId", ""),
        parentMessageTimestamp = j.optLong("parentMessageTimestamp", 0),
        generationId = j.optInt("generationId", 0),
        name = j.optString("name", ""),
        task = j.optString("task", ""),
        status = j.optString("status", SubAgentStatus.PENDING),
        createdAt = j.optLong("createdAt", 0),
        completedAt = j.optLong("completedAt", 0),
        resultSummary = j.optString("resultSummary", ""),
        errorMessage = j.optString("errorMessage", ""),
        toolsWhitelist = j.optString("toolsWhitelist", ""),
        modelConfigId = j.optString("modelConfigId", ""),
        maxRounds = j.optInt("maxRounds", 10),
        roundsUsed = j.optInt("roundsUsed", 0),
        subChatId = j.optString("subChatId", ""),
    )

    init {
        // 启动时从磁盘加载历史会话
        val loaded = loadSessionsFromDisk()
        if (loaded.isNotEmpty()) {
            synchronized(allSessions) { allSessions.addAll(loaded) }
            AppLogger.i(TAG, "从磁盘加载了 ${loaded.size} 个子代理会话")
        }
    }

    // ─── 查询 ──────────────────────────────────────────────────

    /**
     * 按 parentChatId 获取所有子代理，按 generationId 分组返回
     */
    fun getGenerationsForChat(parentChatId: String): List<SubAgentGeneration> {
        val sessions = synchronized(allSessions) {
            allSessions.filter { it.parentChatId == parentChatId }
                .sortedByDescending { it.generationId }
        }
        val liveStates = _activeStates.value
        return sessions
            .groupBy { it.generationId }
            .map { (_, genSessions) ->
                SubAgentGeneration(
                    generationId = genSessions.first().generationId,
                    parentChatId = parentChatId,
                    parentMessageTimestamp = genSessions.first().parentMessageTimestamp,
                    agents = genSessions.map { s ->
                        liveStates[s.agentId] ?: sessionToCardState(s)
                    }
                )
            }
            .sortedByDescending { it.generationId }
    }

    /**
     * 获取最新一代（当前正在执行的）
     */
    fun getCurrentGeneration(parentChatId: String): SubAgentGeneration? {
        return getGenerationsForChat(parentChatId).firstOrNull { it.anyRunning || !it.allCompleted }
            ?: getGenerationsForChat(parentChatId).firstOrNull()
    }

    // ─── 创建与执行 ───────────────────────────────────────────

    /**
     * 创建并同步执行单个子代理（阻塞直到完成）
     * @return SubAgentResultData 包含结果摘要等信息
     */
    suspend fun spawnAndExecute(
        parentChatId: String,
        parentMessageTimestamp: Long,
        generationId: Int,
        task: String,
        toolsWhitelist: List<String>? = null,
        modelConfigId: String? = null,
        maxRounds: Int = 10,
        customSystemPromptTemplate: String? = null,
    ): SubAgentResultData {
        val agentId = UUID.randomUUID().toString()
        val subChatId = "subagent_$agentId"

        // 1. 快速生成名称（规则提取，零延迟）
        val quickName = AgentNameGenerator.extractQuickName(task) ?: "子任务"

        // 2. 创建并持久化 Session
        val session = SubAgentSession(
            agentId = agentId,
            parentChatId = parentChatId,
            parentMessageTimestamp = parentMessageTimestamp,
            generationId = generationId,
            name = quickName,
            task = task,
            status = SubAgentStatus.PENDING,
            toolsWhitelist = toolsWhitelist?.let { org.json.JSONArray(it).toString() } ?: "",
            modelConfigId = modelConfigId ?: "",
            maxRounds = maxRounds,
            subChatId = subChatId,
        )
        saveSession(session)

        // 3. 通知 UI：新增卡片
        updateCardState(agentId, sessionToCardState(session))

        // 4. 构建子代理专用 System Prompt
        val systemPrompt = buildSubAgentSystemPrompt(task, toolsWhitelist)

        // 5. 执行
        val startTime = System.currentTimeMillis()
        var resultSummary = ""
        var errorMessage = ""
        var success = false
        var roundsUsed = 0

        try {
            // 记录启动时间
            agentStartTimes[agentId] = System.currentTimeMillis()

            // 更新状态为 RUNNING
            updateCardStatus(agentId, SubAgentStatus.RUNNING, currentStep = "启动中...")

            // 获取独立的 EnhancedAIService 实例
            val subService = EnhancedAIService.getChatInstance(context, subChatId)

            // 通过 SendMessageOptions 调用
            val resultBuilder = StringBuilder()
            subService.sendMessage(
                SendMessageOptions(
                    message = task,
                    chatId = subChatId,
                    isSubTask = true,
                    stream = false,
                    enableMemoryAutoUpdate = false,
                    customSystemPromptTemplate = customSystemPromptTemplate ?: systemPrompt,
                    chatModelConfigIdOverride = modelConfigId?.takeIf { it.isNotBlank() },
                    onToolInvocation = { toolName ->
                        // 实时更新当前步骤
                        updateCardStatus(
                            agentId,
                            SubAgentStatus.RUNNING,
                            currentStep = "执行: $toolName"
                        )
                    }
                )
            ).collect { chunk ->
                resultBuilder.append(chunk)
            }

            // 解析结果
            val fullResult = resultBuilder.toString()
            resultSummary = extractResultSummary(fullResult)
            if (resultSummary.isBlank()) {
                // 兜底：取最后 200 字符作为摘要
                resultSummary = fullResult.takeLast(200).trim()
            }
            success = true

            // 计算使用的轮次（通过统计 <tool 调用次数估算）
            roundsUsed = Regex("<tool\\s", RegexOption.IGNORE_CASE).findAll(fullResult).count()

        } catch (e: Exception) {
            AppLogger.e(TAG, "子代理执行失败: agentId=$agentId, task=$task", e)
            errorMessage = e.message ?: "Unknown error"
            success = false
        } finally {
            // 释放子代理的 EnhancedAIService 实例
            try {
                EnhancedAIService.releaseChatInstance(subChatId)
            } catch (e: Exception) {
                AppLogger.w(TAG, "释放子代理实例失败: $subChatId", e)
            }
        }

        // 6. 更新最终状态
        val elapsed = System.currentTimeMillis() - startTime
        val finalStatus = if (success) SubAgentStatus.COMPLETED else SubAgentStatus.FAILED

        session.status = finalStatus
        session.completedAt = System.currentTimeMillis()
        session.resultSummary = resultSummary
        session.errorMessage = errorMessage
        session.roundsUsed = roundsUsed
        saveSession(session)

        updateCardState(
            agentId,
            SubAgentCardState(
                agentId = agentId,
                name = session.name,
                task = task,
                status = finalStatus,
                elapsedTimeMs = elapsed,
                resultSummary = resultSummary,
                currentStep = if (success) null else errorMessage,
                subChatId = subChatId,
                roundsUsed = roundsUsed,
                maxRounds = maxRounds,
            )
        )

        AppLogger.i(
            TAG,
            "子代理完成: agentId=$agentId, name=${session.name}, status=$finalStatus, elapsed=${elapsed}ms, rounds=$roundsUsed"
        )

        return SubAgentResultData(
            agentId = agentId,
            agentName = session.name,
            task = task,
            success = success,
            resultSummary = resultSummary,
            errorMessage = errorMessage,
            elapsedTimeMs = elapsed,
            roundsUsed = roundsUsed,
            subChatId = subChatId,
        )
    }

    /**
     * 并行创建并执行多个子代理
     */
    suspend fun spawnAndExecuteParallel(
        parentChatId: String,
        parentMessageTimestamp: Long,
        generationId: Int,
        tasks: List<SubAgentTask>,
        customSystemPromptTemplate: String? = null,
    ): List<SubAgentResultData> {
        return coroutineScope {
            tasks.map { task ->
                async {
                    spawnAndExecute(
                        parentChatId = parentChatId,
                        parentMessageTimestamp = parentMessageTimestamp,
                        generationId = generationId,
                        task = task.task,
                        toolsWhitelist = task.toolsWhitelist,
                        modelConfigId = task.modelConfigId,
                        maxRounds = task.maxRounds,
                        customSystemPromptTemplate = customSystemPromptTemplate,
                    )
                }
            }.awaitAll()
        }
    }

    // ─── 状态更新（内部） ──────────────────────────────────────

    private fun updateCardState(agentId: String, state: SubAgentCardState) {
        _activeStates.value = _activeStates.value.toMutableMap().apply {
            put(agentId, state)
        }
    }

    private fun updateCardStatus(
        agentId: String,
        status: String,
        currentStep: String? = null,
        resultSummary: String? = null
    ) {
        val current = _activeStates.value[agentId] ?: return
        val startTime = agentStartTimes[agentId] ?: System.currentTimeMillis()
        updateCardState(
            agentId,
            current.copy(
                status = status,
                currentStep = currentStep ?: current.currentStep,
                resultSummary = resultSummary ?: current.resultSummary,
                elapsedTimeMs = System.currentTimeMillis() - startTime
            )
        )
    }

    // ─── 辅助方法 ──────────────────────────────────────────────

    private fun sessionToCardState(session: SubAgentSession): SubAgentCardState {
        return SubAgentCardState(
            agentId = session.agentId,
            name = session.name,
            task = session.task,
            status = session.status,
            elapsedTimeMs = if (session.completedAt > 0 && session.createdAt > 0)
                session.completedAt - session.createdAt else 0,
            resultSummary = session.resultSummary.takeIf { it.isNotBlank() },
            subChatId = session.subChatId,
            roundsUsed = session.roundsUsed,
            maxRounds = session.maxRounds,
        )
    }

    /**
     * 构建子代理专用 System Prompt
     */
    private fun buildSubAgentSystemPrompt(
        task: String,
        toolsWhitelist: List<String>?
    ): String {
        return buildString {
            appendLine("你是一个任务执行子代理。你的唯一目标是精确高效地完成以下任务。")
            appendLine()
            appendLine("## 任务")
            appendLine(task)
            appendLine()
            appendLine("## 规则")
            appendLine("1. 专注于完成任务，不要偏离主题，不要与用户闲聊")
            appendLine("2. 完成后在回复的最后用 <result_summary> 标签包裹结果摘要")
            appendLine("3. 如果遇到无法解决的问题，用 <result_summary> 说明原因")
            appendLine("4. 结果摘要要简洁明确，2-3句话概括关键发现/完成的工作")
            appendLine("5. 禁止调用 spawn_subagent 和 spawn_subagents_parallel 工具，你不具备创建子代理的权限")
            if (!toolsWhitelist.isNullOrEmpty()) {
                appendLine("6. 你只能使用以下工具：${toolsWhitelist.joinToString("、")}。其他工具不可用，不要尝试调用。")
            }
            appendLine()
            appendLine("## 结果摘要格式（必须在回复末尾包含）")
            appendLine("<result_summary>")
            appendLine("[用2-3句话概括你完成了什么、得到了什么关键结果]")
            appendLine("</result_summary>")
        }
    }

    /**
     * 从子代理的回复中提取 <result_summary> 内容
     */
    private fun extractResultSummary(fullResult: String): String {
        val pattern = Regex(
            "<result_summary>(.*?)</result_summary>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return pattern.find(fullResult)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    /**
     * 获取下一个 generationId（用于主代理确定新一批子代理的代号）
     */
    fun getNextGenerationId(parentChatId: String): Int {
        val maxGenId = synchronized(allSessions) {
            allSessions.filter { it.parentChatId == parentChatId }
                .maxOfOrNull { it.generationId } ?: 0
        }
        return maxGenId + 1
    }
}