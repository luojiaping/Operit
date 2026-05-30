# Operit 子代理系统（Sub-Agent System）技术文档

> 版本: v2.0 | 日期: 2026-05-30  
> 分支: `dev_subagent` | 基于: `main` (9f409ca8)  
> 关联 Issue: 子代理并行执行 / Goal Mode / Orchestrator 调度器

---

## 目录

1. [背景与动机](#一背景与动机)
2. [行业对标分析](#二行业对标分析)
3. [核心设计决策](#三核心设计决策)
4. [架构总览](#四架构总览)
5. [数据模型详解](#五数据模型详解)
6. [核心组件详解](#六核心组件详解)
7. [工具注册与 Prompt 系统](#七工具注册与-prompt-系统)
8. [对话循环集成方案](#八对话循环集成方案)
9. [UI 层设计与实现](#九ui-层设计与实现)
10. [编译问题排查与修复](#十编译问题排查与修复)
11. [文件清单与变更记录](#十一文件清单与变更记录)
12. [剩余 TODO 与后续规划](#十二剩余-todo-与后续规划)
13. [风险与缓解措施](#十三风险与缓解措施)

---

## 一、背景与动机

### 1.1 问题描述

Operit 的 AI 对话模型是**单线程串行**的：主代理收到用户请求 → 调用工具 → 等待结果 → 再调用工具 → 最终回复。这个模式在以下场景存在明显瓶颈：

- **多文件分析**：主代理需要依次读取 10 个文件，每个文件读取一次工具调用，串行耗时长
- **独立子任务**：如"同时扫描安全漏洞 + 分析代码风格 + 检查依赖版本"，三个任务互不依赖却必须串行
- **复杂推理**：需要一个代理专注做深度分析，另一个代理同时做信息收集

### 1.2 解决思路

参考 2026 年主流 AI Agent 工具的演进方向：

| 工具 | 子代理能力 | 核心特征 |
|------|-----------|---------|
| **Claude Code** | Subagents + `/goal` | 子代理拥有独立对话、工具白名单、结果回传主代理 |
| **Cursor 3.x** | Parallel Agents | 多个 Agent 并行处理不同文件/任务 |
| **OpenClaw** | Multi-model Sub-agents | 不同子代理可使用不同模型 |
| **Hermes Agent** | Multiple Agents Stack | 24/7 后台运行的子代理 |
| **Devin** | Subagents + Cloud Handoff | 子代理可交接给云端执行 |

核心设计哲学：**子代理 = 一个"长时间运行的工具调用"**。

---

## 二、行业对标分析

### 2.1 Claude Code Subagents（主要参考）

Claude Code 的子代理系统是本次设计的主要参考对象：

```
主代理 → spawn_subagent(task, tools_whitelist)
          ↓
          子代理独立运行（隔离的对话历史 + 工具集）
          ↓
          结果摘要回传主代理
          ↓
          主代理基于结果继续推理
```

**关键特征**：
- 子代理拥有**独立的对话历史**，不污染主代理上下文
- 主代理通过 `tools_whitelist` **显式授权**子代理的工具权限
- 子代理执行期间，主代理处于**等待状态**（类似等待一个长时间的工具调用）
- 结果以**摘要形式**回传，避免子代理的完整对话历史占用主代理的上下文窗口

### 2.2 我们的改进

在 Claude Code 的基础上，Operit 做了以下改进：

| 特性 | Claude Code | Operit SubAgent |
|------|------------|-----------------|
| 子代理面板 | 终端文本输出 | **嵌入式 UI 卡片**，实时状态动画 |
| 历史管理 | 无明显机制 | **按 generation 分代**，自动折叠历史 |
| 并行执行 | 支持 | 支持（`spawn_subagents_parallel`） |
| 持久化 | 对话数据可能丢失 | **ObjectBox 永久保存**每个子代理会话 |
| 进步可见性 | 靠终端输出 | **实时 StateFlow 推送**卡片状态更新 |
| 名称生成 | 用户手动或无 | **智能自动命名**（规则 + 可选 LLM） |

---

## 三、核心设计决策

### 3.1 决策一：子代理 = 普通工具调用

**问题**：子代理系统是否需要修改对话循环的核心逻辑？

**决策**：**不需要**。子代理在底层对话循环中就是一个普通的工具调用，只是执行时间较长。

**理由**：
Operit 的对话循环结构如下：
```
sendMessage → processStreamCompletion → 
  ├─ 有工具调用 → handleToolInvocation → processToolResults → 递归调用 processStreamCompletion
  └─ 无工具调用 → finalizeAssistantResponse（结束）
```

`spawn_subagent` 工具注册在 `ToolRegistration.kt` 中，与其他工具（`read_file`、`visit_web` 等）地位完全相同。区别仅在于：
- 执行时间：几秒到几分钟（vs 普通工具的毫秒到秒级）
- 执行过程中通过 StateFlow 推送进度给 UI

这样做的好处：
1. **零侵入**：不修改 `sendMessage`、`processStreamCompletion`、`handleToolInvocation` 等核心方法
2. **可测试**：子代理工具可以像普通工具一样被单元测试
3. **向后兼容**：禁用子代理工具后，系统行为与之前完全一致

### 3.2 决策二：对话数据永久保留

**问题**：子代理的对话数据是临时的还是持久化的？

**决策**：**永久保留**，使用 ObjectBox 持久化。

**理由**：
- 子代理的执行过程是**有价值的审计记录**，用户可能需要回溯"为什么 AI 做了这个决定"
- UI 层面通过"按 generation 折叠"来控制信息密度，而不是删除数据
- ObjectBox 是 Operit 已有的 ORM 框架，新增 Entity 无额外依赖

**UI 层面的管理策略**：
```
当前代（正在执行/刚完成）→ 展开显示所有卡片
上一代 → 默认折叠，显示"历史 (2代, 5个)"
更早的代 → 仅在"全部历史"中可见
```

### 3.3 决策三：子代理面板嵌入主消息下方

**问题**：子代理的执行状态展示在哪里？

**决策**：嵌入到触发它的那条主代理消息的**正下方**。

**理由**：
- 主代理发出了"我来派子代理处理"的消息后，下方直接出现子代理面板，因果关系清晰
- 主代理处于等待状态（不会产生新消息），所以面板不会被新消息推走
- 用户可以同时看到"主代理说了什么"和"子代理在做什么"

### 3.4 决策四：智能命名

**问题**：子代理如何命名？

**决策**：双层策略——先规则提取（零延迟），后可选 LLM 优化。

**命名规则**：
```
任务: "帮我分析 app/src/main/java 下的项目架构"
→ 规则提取: 去掉"帮我" → "分析app/src/main/java下的项目架构" → 截断8字 → "分析app/sr"
→ LLM优化: "架构分析"（如果启用）
```

---

## 四、架构总览

### 4.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI 层                                    │
│                                                                  │
│  ChatArea.kt                                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  chatHistory.forEachIndexed { message ->                 │    │
│  │      MessageItem(message)           // 原有的消息渲染     │    │
│  │      if (message.sender == "ai") {                       │    │
│  │          SubAgentPanelView(generations)  // ← 新增       │    │
│  │      }                                                   │    │
│  │  }                                                       │    │
│  │                                                          │    │
│  │  SubAgentDetailSheet(selectedAgent)  // ← 新增：详情弹窗  │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↑ StateFlow 实时推送                                     │
├─────────────────────────────────────────────────────────────────┤
│                     管理层                                       │
│                                                                  │
│  SubAgentManager (Singleton)                                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  _activeStates: MutableStateFlow<Map<agentId, CardState>>│   │
│  │                                                          │    │
│  │  spawnAndExecute()     → 创建+执行单个子代理              │    │
│  │  spawnAndExecuteParallel() → 并行执行多个子代理           │    │
│  │  getGenerationsForChat() → 按 generation 查询            │    │
│  │  getNextGenerationId()   → 获取下一个代号                │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↕ ObjectBox 读写                                         │
├─────────────────────────────────────────────────────────────────┤
│                     执行层                                       │
│                                                                  │
│  StandardSubAgentTools                                           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  spawnSubAgent(tool)         → 单子代理工具              │    │
│  │  spawnSubAgentsParallel(tool) → 并行子代理工具           │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓ 调用                                                    │
│  EnhancedAIService.getChatInstance(subChatId)                    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  .sendMessage(SendMessageOptions(                        │    │
│  │      message = task,                                     │    │
│  │      isSubTask = true,        // 不更新 UI 状态          │    │
│  │      stream = false,          // 同步等待完成            │    │
│  │      customSystemPromptTemplate = subAgentPrompt,        │    │
│  │      onToolInvocation = { updateCardStatus(...) }        │    │
│  │  ))                                                      │    │
│  └─────────────────────────────────────────────────────────┘    │
│         ↓ 结果解析                                                │
│  extractResultSummary(fullResult) → <result_summary>...</>       │
├─────────────────────────────────────────────────────────────────┤
│                     持久层                                       │
│                                                                  │
│  ObjectBox: SubAgentSession Entity                               │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  agentId | parentChatId | generationId | name | task |   │    │
│  │  status | createdAt | completedAt | resultSummary |      │    │
│  │  roundsUsed | subChatId | ...                            │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 时序图：单子代理执行流程

```
用户          主代理(LLM)       SubAgentManager      子代理(LLM)        UI(ChatArea)
 │                │                  │                    │                  │
 │  "分析项目架构"  │                  │                    │                  │
 │──────────────→│                  │                    │                  │
 │                │  spawn_subagent  │                    │                  │
 │                │─────────────────→│                    │                  │
 │                │                  │  创建Session        │                  │
 │                │                  │──────────          │                  │
 │                │                  │  updateCardState   │                  │
 │                │                  │───────────────────────────────────────→│
 │                │                  │                    │     显示卡片(PENDING)
 │                │                  │  sendMessage       │                  │
 │                │                  │───────────────────→│                  │
 │                │                  │                    │  LLM推理+工具调用 │
 │                │                  │                    │──────────        │
 │                │                  │  onToolInvocation  │                  │
 │                │                  │←───────────────────│                  │
 │                │                  │  updateCardStatus  │                  │
 │                │                  │───────────────────────────────────────→│
 │                │                  │                    │     更新卡片(执行中)
 │                │                  │                    │  ...更多轮次...   │
 │                │                  │  Stream完成         │                  │
 │                │                  │←───────────────────│                  │
 │                │                  │  解析result_summary │                  │
 │                │                  │──────────          │                  │
 │                │                  │  保存Session        │                  │
 │                │                  │──────────          │                  │
 │                │  ToolResult      │  updateCardState   │                  │
 │                │←─────────────────│───────────────────────────────────────→│
 │                │                  │                    │     更新卡片(COMPLETED)
 │                │  LLM基于结果推理  │                    │                  │
 │                │──────────        │                    │                  │
 │  最终回复       │                  │                    │                  │
 │←──────────────│                  │                    │                  │
```

### 4.3 关键设计原则

1. **零侵入对话循环**：子代理作为普通工具调用，不修改 `sendMessage` → `processStreamCompletion` → `handleToolInvocation` → `processToolResults` 递归链
2. **隔离性**：每个子代理使用独立的 `EnhancedAIService` 实例（通过 `getChatInstance(subChatId)`），拥有独立对话历史
3. **实时性**：通过 `StateFlow<Map<agentId, CardState>>` 实时推送状态，UI 层用 `collectAsState()` 订阅
4. **可审计性**：所有子代理会话数据永久保存在 ObjectBox 中
5. **智能默认值**：工具白名单默认只读安全、名称默认自动提取、轮次默认 10

---

## 五、数据模型详解

### 5.1 SubAgentSession（ObjectBox 持久化实体）

**文件**: `data/model/SubAgentSession.kt`

```kotlin
@Entity
data class SubAgentSession(
    @Id var id: Long = 0,
    @Index var agentId: String = "",              // UUID，子代理唯一标识
    @Index var parentChatId: String = "",         // 主对话的 chatId
    var parentMessageTimestamp: Long = 0L,        // 触发此子代理的主代理消息时间戳
    @Index var generationId: Int = 0,             // 第几代（同一批创建的子代理共享 generationId）
    var name: String = "",                        // 智能生成的名称，如 "架构分析"
    var task: String = "",                        // 任务描述原文
    var status: String = SubAgentStatus.PENDING,  // 状态常量
    var createdAt: Long = System.currentTimeMillis(),
    var completedAt: Long = 0L,                   // 完成时间戳
    var resultSummary: String = "",               // <result_summary> 标签中的内容
    var errorMessage: String = "",                // 失败时的错误信息
    var toolsWhitelist: String = "",              // JSON 数组，空 = 默认白名单
    var modelConfigId: String = "",               // 空 = 继承主代理配置
    var maxRounds: Int = 10,                      // 最大工具调用轮次
    var roundsUsed: Int = 0,                      // 实际使用的轮次
    var subChatId: String = "",                   // 子代理的独立 chatId
)
```

**设计要点**：
- `generationId` 用于将同一批创建的子代理归为一组（如"主代理同时派了3个子代理"）
- `parentMessageTimestamp` 用于关联到主对话中的具体消息（UI 面板嵌入位置）
- `subChatId = "subagent_$agentId"` 用于创建独立的 `EnhancedAIService` 实例
- `toolsWhitelist` 用 JSON 字符串存储，空字符串表示使用默认安全工具集

### 5.2 SubAgentStatus（状态常量）

**文件**: `data/model/SubAgentSession.kt`

```kotlin
object SubAgentStatus {
    const val PENDING = "pending"       // 已创建，等待执行
    const val RUNNING = "running"       // 执行中
    const val COMPLETED = "completed"   // 正常完成
    const val FAILED = "failed"         // 执行失败
    const val CANCELLED = "cancelled"   // 被取消（预留）
}
```

**状态流转**：
```
PENDING → RUNNING → COMPLETED
                  → FAILED
         → CANCELLED（预留）
```

### 5.3 SubAgentCardState（运行时 UI 状态）

**文件**: `api/chat/subagent/SubAgentModels.kt`

```kotlin
data class SubAgentCardState(
    val agentId: String,
    val name: String,                 // "架构分析"
    val task: String,                 // "分析项目架构..."
    val status: String,               // SubAgentStatus 常量
    val elapsedTimeMs: Long = 0,      // 已耗时
    val resultSummary: String? = null, // 完成后的摘要
    val currentStep: String? = null,   // "执行: grep_code" 等实时步骤
    val subChatId: String = "",        // 点击查看详情时需要
    val roundsUsed: Int = 0,
    val maxRounds: Int = 10,
)
```

**设计要点**：
- 这是**纯内存数据**，不持久化，由 `SubAgentManager._activeStates: MutableStateFlow` 维护
- UI 层通过 `collectAsState()` 订阅，实现实时更新
- `currentStep` 在每次工具调用时更新（如"执行: read_file"），让用户看到子代理在干什么

### 5.4 SubAgentGeneration（代次聚合）

**文件**: `api/chat/subagent/SubAgentModels.kt`

```kotlin
data class SubAgentGeneration(
    val generationId: Int,
    val parentChatId: String,
    val parentMessageTimestamp: Long,
    val agents: List<SubAgentCardState>,
) {
    val allCompleted: Boolean  // 所有代理都已完成
    val anyRunning: Boolean    // 有代理正在运行
    val completedCount: Int
    val failedCount: Int
}
```

### 5.5 SubAgentResultData / SubAgentsParallelResultData

**文件**: `api/chat/subagent/SubAgentResultData.kt`

```kotlin
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
    fun toDisplayString(): String  // 供 StandardSubAgentTools 包装为 StringResultData
}
```

**注意**：这个类**不继承 `ToolResultData`**（sealed class 无法跨包继承），而是通过 `StandardSubAgentTools` 用 `StringResultData(result.toDisplayString())` 包装后返回给对话循环。

---

## 六、核心组件详解

### 6.1 SubAgentManager（管理器）

**文件**: `api/chat/subagent/SubAgentManager.kt`

**职责**：
- 管理所有子代理实例的生命周期
- 提供创建、执行、取消、查询接口
- 通过 StateFlow 向 UI 层暴露实时状态
- 负责 ObjectBox 持久化读写

**关键实现细节**：

```kotlin
class SubAgentManager(private val context: Context) {
    companion object {
        @Volatile private var instance: SubAgentManager? = null
        fun getInstance(context: Context): SubAgentManager  // 单例
    }

    // 实时状态（供 UI 订阅）
    private val _activeStates = MutableStateFlow<Map<String, SubAgentCardState>>(emptyMap())
    val activeStates: StateFlow<Map<String, SubAgentCardState>> = _activeStates.asStateFlow()

    // 启动时间追踪（agentId -> 启动时间戳）
    private val agentStartTimes = ConcurrentHashMap<String, Long>()
}
```

**`spawnAndExecute()` 核心流程**：

```
1. 生成 agentId (UUID) 和 subChatId = "subagent_$agentId"
2. 调用 AgentNameGenerator.extractQuickName(task) 快速命名
3. 创建 SubAgentSession 并 ObjectBox.put() 持久化
4. updateCardState(agentId, PENDING) → UI 显示新卡片
5. agentStartTimes[agentId] = now  → 记录启动时间
6. updateCardStatus(agentId, RUNNING, "启动中...")
7. EnhancedAIService.getChatInstance(subChatId) → 获取隔离实例
8. sendMessage(SendMessageOptions(
       message = task,
       chatId = subChatId,
       isSubTask = true,        ← 关键：不更新全局 UI 状态
       stream = false,          ← 关键：同步等待完成
       customSystemPromptTemplate = systemPrompt,
       onToolInvocation = { toolName ->
           updateCardStatus(agentId, RUNNING, "执行: $toolName")
       }
   )).collect { resultBuilder.append(it) }
9. 解析 resultBuilder 中的 <result_summary> 标签
10. releaseChatInstance(subChatId) → 释放资源
11. updateCardStatus(agentId, COMPLETED/FAILED)
12. ObjectBox.put(session) → 持久化最终状态
13. return SubAgentResultData
```

**`isSubTask = true` 的关键作用**：
查看 `EnhancedAIService.sendMessage()` 源码，当 `isSubTask = true` 时：
- 跳过 `startAiService()` — 不启动前台服务
- 跳过 `_inputProcessingState` 更新 — 不显示"正在处理..."状态
- 跳过 `stopAiService()` — 不停止前台服务
- 跳过通知相关逻辑

这使得子代理的执行对主对话的 UI 完全透明。

### 6.2 AgentNameGenerator（名称生成器）

**文件**: `api/chat/subagent/AgentNameGenerator.kt`

**双层策略**：

**Layer 1: 规则提取（零延迟，always-on）**
```kotlin
fun extractQuickName(task: String): String? {
    // 1. 如果 ≤ 8 字符，直接返回
    // 2. 去掉噪音动词前缀："帮我"、"请"、"分析一下"、"看一下"等
    // 3. 去掉噪音后缀："一下"、"看看"、"吧"等
    // 4. 截断到 MAX_NAME_LENGTH (8字符)
}
```

**Layer 2: LLM 优化（可选，异步）**
```kotlin
suspend fun generateSmartName(task: String, context: Context): String {
    // 调用 FunctionType.SUMMARY 的 AIService
    // Prompt: "请用1-4个中文字概括以下任务的核心目标"
    // 超时 5 秒，失败回退到规则提取
}
```

### 6.3 StandardSubAgentTools（工具实现）

**文件**: `core/tools/defaultTool/standard/StandardSubAgentTools.kt`

**两个工具方法**：

**`spawnSubAgent(tool: AITool)`**：
```
1. 解析参数: task, tools_whitelist, max_rounds, model_config_id
2. 获取 parentChatId（从 __operit_package_chat_id 参数）
3. 调用 subAgentManager.getNextGenerationId(parentChatId)
4. 调用 subAgentManager.spawnAndExecute(...)
5. 返回 ToolResult(success, StringResultData(result.toDisplayString()))
```

**`spawnSubAgentsParallel(tool: AITool)`**：
```
1. 解析 tasks JSON 数组
2. 转换为 List<SubAgentTask>
3. 调用 subAgentManager.spawnAndExecuteParallel(...)
4. 聚合结果，返回 ToolResult
```

---

## 七、工具注册与 Prompt 系统

### 7.1 工具注册

**文件**: `core/tools/ToolRegistration.kt`（末尾新增）

```kotlin
val subAgentTools = StandardSubAgentTools(context)

handler.registerTool(
    name = "spawn_subagent",
    descriptionGenerator = { tool ->
        val task = tool.parameters.find { it.name == "task" }?.value ?: ""
        s(R.string.toolreg_spawn_subagent_desc, task, maxRounds)
    },
    executor = { tool ->
        runBlocking(Dispatchers.IO) { subAgentTools.spawnSubAgent(tool) }
    }
)

handler.registerTool(
    name = "spawn_subagents_parallel",
    descriptionGenerator = { _ -> s(R.string.toolreg_spawn_subagents_parallel_desc) },
    executor = { tool ->
        runBlocking(Dispatchers.IO) { subAgentTools.spawnSubAgentsParallel(tool) }
    }
)
```

**设计要点**：
- 使用 `runBlocking(Dispatchers.IO)` 桥接同步 executor 和 suspend 函数（与音乐播放等工具一致）
- `descriptionGenerator` 动态生成描述，包含任务内容

### 7.2 工具 Prompt（中英文）

**文件**: `core/config/SystemToolPromptsInternal.kt`

**英文版**（`internalToolCategories` 末尾新增 `Sub-Agent Tools` 分类）：
```
ToolPrompt(
    name = "spawn_subagent",
    description = "Create a sub-agent to independently execute a specified task. 
                   The sub-agent has its own conversation history and tool set,
                   and will return the result upon completion.",
    parametersStructured = [
        task (required, string),
        tools_whitelist (optional, string - JSON array or "all"),
        max_rounds (optional, integer, default=10),
        model_config_id (optional, string)
    ]
)
```

**中文版**（`internalToolCategoriesCn` 末尾新增 `子代理工具` 分类）：
```
ToolPrompt(
    name = "spawn_subagent",
    description = "创建一个子代理来独立执行指定任务。子代理拥有自己的对话历史和工具集，
                   完成后将结果返回给你。",
    // ...同上参数
)
```

### 7.3 字符串资源

**`values/strings.xml`**（中文）：
```xml
<string name="toolreg_spawn_subagent_desc">创建子代理执行任务「%1$s」（最多%2$s轮）</string>
<string name="toolreg_spawn_subagents_parallel_desc">并行创建多个子代理同时执行不同任务</string>
```

**`values-en/strings.xml`**（英文）：
```xml
<string name="toolreg_spawn_subagent_desc">Create sub-agent to execute task '%1$s' (max %2$s rounds)</string>
<string name="toolreg_spawn_subagents_parallel_desc">Create multiple sub-agents to execute different tasks in parallel</string>
```

---

## 八、对话循环集成方案

### 8.1 为什么不需要修改对话循环

这是本方案最核心的设计决策。让我们从源码层面证明这一点。

**现有对话循环**（`EnhancedAIService.kt`）：

```
sendMessage()
  ↓
processStreamCompletion()
  ├─ 提取工具调用: extractToolInvocations(content)
  ├─ 如果有工具调用 → handleToolInvocation()
  │   └─ ToolExecutionManager.executeInvocations()
  │       └─ 逐个调用 toolHandler.getToolExecutorOrActivate(toolName).invoke(tool)
  │           └─ spawn_subagent 在这里被执行（阻塞等待）
  │       └─ 返回 List<ToolResult>
  │   └─ processToolResults()
  │       └─ 把 ToolResult 加入对话历史
  │       └─ 再次调用 LLM → processStreamCompletion()（递归）
  └─ 如果没有工具调用 → finalizeAssistantResponse()（结束）
```

**子代理工具在这个流程中的位置**：

```
handleToolInvocation()
  └─ ToolExecutionManager.executeInvocations()
      └─ 当 tool.name == "spawn_subagent" 时
          └─ StandardSubAgentTools.spawnSubAgent(tool)
              └─ SubAgentManager.spawnAndExecute(...)
                  └─ 子代理独立运行（可能几分钟）
                  └─ 返回 SubAgentResultData
              └─ 返回 ToolResult(success, StringResultData("结果摘要"))
      └─ 其他工具正常执行
  └─ processToolResults(results)
      └─ LLM 收到子代理结果，继续推理
```

**关键**：对 `ToolExecutionManager` 和 `processToolResults` 来说，`spawn_subagent` 就是一个普通的工具，返回一个普通的 `ToolResult`。唯一特殊的是它的执行时间较长。

### 8.2 工具并行执行的注意事项

查看 `ToolExecutionManager.executeInvocations()` 中的并行逻辑：

```kotlin
val parallelizableToolNames = setOf(
    "list_files", "read_file", "read_file_part", "read_file_full", "file_exists",
    "find_files", "file_info", "grep_code", "calculate", "ffmpeg_info",
    "visit_web", "download_file"
)
val (parallelInvocations, serialInvocations) = injectedInvocations.partition {
    parallelizableToolNames.contains(it.tool.name)
}
```

**`spawn_subagent` 不在 `parallelizableToolNames` 中**，因此会被归入串行执行。这是正确的设计：
- 如果主代理同时调用 `read_file` 和 `spawn_subagent`，先执行 `read_file`（快），再执行 `spawn_subagent`（慢）
- 如果主代理需要并行多个子代理，应使用 `spawn_subagents_parallel` 工具

### 8.3 结果回传机制

子代理的结果通过以下路径回传给主代理的 LLM：

```
SubAgentResultData
  ↓ .toDisplayString()
StringResultData("子代理完成: 架构分析 - 发现3个核心模块... (耗时12s, 5轮)")
  ↓ 包装为 ToolResult
ToolResult(toolName="spawn_subagent", success=true, result=StringResultData(...))
  ↓ processToolResults()
加入对话历史: PromptTurn(kind=TOOL_RESULT, content="...", toolName="spawn_subagent")
  ↓ 再次调用 LLM
主代理 LLM 收到子代理结果，基于此继续推理和回复
```

---

## 九、UI 层设计与实现

### 9.1 组件结构

```
ui/features/chat/components/subagent/
├── SubAgentStatusIcon.kt     // 状态图标（脉冲/转圈/勾/叉）
├── SubAgentCardView.kt       // 单个子代理卡片
├── SubAgentPanelView.kt      // 面板容器（当前代展开 + 历史折叠）
└── SubAgentDetailSheet.kt    // 底部弹窗（完整对话记录）
```

### 9.2 ChatArea.kt 中的集成点

**文件**: `ui/features/chat/components/ChatArea.kt`

**新增的 StateFlow 订阅**：
```kotlin
val subAgentManager = remember { SubAgentManager.getInstance(context) }
val activeSubAgentStates by subAgentManager.activeStates.collectAsState()
val subAgentGenerations = remember(activeSubAgentStates, currentChatId) {
    subAgentManager.getGenerationsForChat(currentChatId)
}
var selectedSubAgent by remember { mutableStateOf<SubAgentCardState?>(null) }
```

**消息循环中的注入**：
```kotlin
chatHistory.forEachIndexed { actualIndex, message ->
    key(message.timestamp) {
        Box(...) {
            MessageItem(...)  // 原有消息渲染

            // 子代理面板：在 AI 消息下方显示
            val relatedGens = remember(subAgentGenerations, message.timestamp, message.sender) {
                if (message.sender == "ai") {
                    subAgentGenerations.filter { gen ->
                        gen.agents.any { it.status == RUNNING || it.status == PENDING } ||
                        gen.parentMessageTimestamp == message.timestamp
                    }
                } else emptyList()
            }
            if (relatedGens.isNotEmpty()) {
                SubAgentPanelView(
                    generations = relatedGens,
                    onAgentClick = { agent -> selectedSubAgent = agent }
                )
            }
        }
    }
}
```

**详情弹窗**：
```kotlin
selectedSubAgent?.let { agent ->
    SubAgentDetailSheet(
        agentState = agent,
        chatHistory = emptyList(), // TODO: 从 ObjectBox 加载
        onDismiss = { selectedSubAgent = null }
    )
}
```

### 9.3 状态图标动画

**SubAgentStatusIcon**：
| 状态 | 图标 | 动画 |
|------|------|------|
| PENDING | ↻ Refresh | 透明度脉冲（0.3 ↔ 1.0） |
| RUNNING | ◎ CircularProgressIndicator | 旋转（Material 默认） |
| COMPLETED | ✓ Check | 静态，tertiary 色 |
| FAILED | ✕ Close | 静态，error 色 |
| CANCELLED | ✕ Close | 静态，半透明 |

### 9.4 卡片颜色方案

| 状态 | 容器色 |
|------|--------|
| RUNNING | `primaryContainer` |
| COMPLETED | `tertiaryContainer` |
| FAILED | `errorContainer` |
| PENDING | `surfaceVariant` |
| CANCELLED | `surfaceVariant.copy(alpha=0.5f)` |

---

## 十、编译问题排查与修复

在开发过程中发现并修复了以下可能导致编译失败的问题：

### 10.1 sealed class 跨包继承

**问题**：`SubAgentResultData`（在 `api.chat.subagent` 包）试图继承 `ToolResultData`（在 `core.tools` 包）。Kotlin 的 `sealed class` 只允许同包子类。

**修复**：`SubAgentResultData` 改为独立的 `@Serializable data class`（不继承 `ToolResultData`），通过 `toDisplayString()` 方法输出文本，由 `StandardSubAgentTools` 用 `StringResultData(result.toDisplayString())` 包装。

### 10.2 AIService.sendMessage() 的 Context 参数

**问题**：`AIService.sendMessage()` 的 `context` 参数是 `Context`（非空），但 `AgentNameGenerator` 最初传了 `null`。

**修复**：`generateSmartName()` 改为接收 `Context` 参数，通过 `EnhancedAIService.getAIServiceForFunction(context, FunctionType.SUMMARY)` 获取服务。

### 10.3 elapsed time 计算 bug

**问题**：`updateCardStatus()` 中的耗时计算使用了 `current.elapsedTimeMs.takeIf { it > 0 } ?: System.currentTimeMillis()`，这在首次调用时会导致 `elapsedTimeMs = now - now = 0`，后续调用也无法正确追踪。

**修复**：引入 `agentStartTimes: ConcurrentHashMap<String, Long>`，在 `spawnAndExecute()` 开始时记录启动时间，`updateCardStatus()` 使用 `System.currentTimeMillis() - startTime` 计算。

### 10.4 未使用的 import

**问题**：初始代码中包含未使用的 `CoroutineScope`、`withContext`、`kotlinx.serialization.json.Json` 等导入。

**修复**：清理所有未使用的 import。

---

## 十一、文件清单与变更记录

### 11.1 新增文件（12 个）

| 文件路径 | 行数 | 说明 |
|---------|------|------|
| `data/model/SubAgentSession.kt` | 42 | ObjectBox Entity + SubAgentStatus |
| `api/chat/subagent/SubAgentModels.kt` | 49 | SubAgentCardState, SubAgentGeneration, SubAgentTask |
| `api/chat/subagent/SubAgentManager.kt` | 380 | 核心管理器（生命周期、执行、查询、StateFlow） |
| `api/chat/subagent/SubAgentResultData.kt` | 53 | 工具返回值数据类 |
| `api/chat/subagent/AgentNameGenerator.kt` | 122 | 智能名称生成器（规则 + LLM） |
| `core/tools/.../StandardSubAgentTools.kt` | 169 | 工具实现（spawn + parallel） |
| `ui/.../subagent/SubAgentStatusIcon.kt` | 102 | 状态图标 + formatElapsedTime |
| `ui/.../subagent/SubAgentCardView.kt` | 98 | 子代理卡片 |
| `ui/.../subagent/SubAgentPanelView.kt` | 123 | 面板容器（展开 + 折叠历史） |
| `ui/.../subagent/SubAgentDetailSheet.kt` | 181 | 详情弹窗 |
| `docs/SUBAGENT_ARCHITECTURE_PLAN.md` | — | 本文档 |

### 11.2 修改文件（5 个）

| 文件路径 | 变更说明 |
|---------|---------|
| `core/tools/ToolRegistration.kt` | 末尾新增 `spawn_subagent` + `spawn_subagents_parallel` 注册 |
| `core/config/SystemToolPromptsInternal.kt` | 英文/中文各新增 `Sub-Agent Tools` 工具分类 |
| `ui/features/chat/components/ChatArea.kt` | 新增 SubAgentManager 状态订阅、SubAgentPanelView 注入、SubAgentDetailSheet |
| `res/values/strings.xml` | +2 条中文字符串 |
| `res/values-en/strings.xml` | +2 条英文字符串 |

### 11.3 Git Commits

```
f0e2486b  feat: implement sub-agent system - Phase 1-3
59837678  feat(subagent): integrate SubAgentPanel into ChatArea message list
09a8bfb0  fix(subagent): resolve compilation issues
```

---

## 十二、剩余 TODO 与后续规划

### 12.1 已完成功能

- [x] ObjectBox 持久化实体
- [x] SubAgentManager 生命周期管理
- [x] spawn_subagent 工具（单子代理）
- [x] spawn_subagents_parallel 工具（并行）
- [x] AgentNameGenerator 规则提取
- [x] 工具 Prompt（中英文）
- [x] 字符串资源
- [x] SubAgentPanelView + SubAgentCardView
- [x] SubAgentDetailSheet（框架）
- [x] ChatArea 集成
- [x] 编译问题修复

### 12.2 待完成

- [ ] **SubAgentDetailSheet 对话历史加载**：目前 `chatHistory` 传 `emptyList()`，需要实现从 ObjectBox 按 `subChatId` 加载 `MessageEntity`
- [ ] **AgentNameGenerator LLM 版本**：`generateSmartName()` 已实现但未在 `spawnAndExecute()` 中调用（目前只用规则提取）
- [ ] **CursorStyleChatMessage 集成**：当前 SubAgentPanelView 注入在 ChatArea 级别（两种样式都生效），但可以进一步优化 Cursor 样式下的视觉效果
- [ ] **取消子代理**：`cancelAgent(agentId)` 方法预留但未实现
- [ ] **超时机制**：子代理长时间无响应时的自动取消
- [ ] **工具白名单实际过滤**：`tools_whitelist` 参数已解析，但子代理实际仍使用全部工具（需要在 `EnhancedAIService.sendMessage` 中注入工具过滤逻辑）
- [ ] **Goal Mode**：目标驱动模式（参考 Claude Code `/goal`）
- [ ] **Orchestrator**：编排器，自动拆分任务并分发给多个子代理

### 12.3 后续规划路线

```
Phase 1 (已完成): 基础子代理系统
  → spawn_subagent + spawn_subagents_parallel + UI 面板

Phase 2 (下一步): 完善体验
  → DetailSheet 对话历史 + 取消 + 超时 + 工具白名单过滤

Phase 3: Goal Mode
  → 在 EnhancedAIService 中添加 goalMode 标记
  → processStreamCompletion 末尾：goal 激活时不结束，检查条件后继续

Phase 4: Orchestrator
  → AgentOrchestrator 管理器
  → 自动任务拆分 → 并行分发 → 结果聚合
```

---

## 十三、风险与缓解措施

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 子代理执行时间过长 | 主代理长时间阻塞，用户体验差 | 1. 添加超时机制（maxRounds 兜底）<br>2. 添加取消按钮（Phase 2）<br>3. UI 实时显示进度 |
| 多子代理并发请求 LLM API | 触发 Provider 限流 | 1. SubAgentManager 中加入并发限制<br>2. 可为子代理指定不同 Provider |
| 子代理消耗大量 Token | 成本不可控 | 1. 默认工具白名单限制<br>2. 支持为子代理指定轻量模型<br>3. maxRounds 默认 10 |
| ObjectBox 数据膨胀 | 存储空间增长 | 当前"永久保留"策略，后续可添加清理策略（如只保留最近 N 代） |
| UI 层子代理面板导致列表卡顿 | 滑动不流畅 | 1. 使用 `remember` 缓存<br>2. 历史代默认折叠<br>3. LazyColumn 复用机制 |

---

## 附录 A：工具白名单默认值

```kotlin
// 安全工具集（默认）—— 只读 + 计算 + 网页浏览
val DEFAULT_SAFE_TOOLS = listOf(
    "read_file", "read_file_part", "list_files", "find_files", "file_info",
    "grep_code", "grep_context", "calculate",
    "visit_web", "download_file",
    "get_device_info"
)

// 传 tools_whitelist = "all" 时使用全部工具
// 传 tools_whitelist = ["read_file", "grep_code"] 时使用指定工具
// 不传 tools_whitelist 时使用 DEFAULT_SAFE_TOOLS
```

## 附录 B：子代理 System Prompt 模板

```
你是一个任务执行子代理。你的唯一目标是精确高效地完成以下任务。

## 任务
{task}

## 规则
1. 专注于完成任务，不要偏离主题，不要与用户闲聊
2. 完成后在回复的最后用 <result_summary> 标签包裹结果摘要
3. 如果遇到无法解决的问题，用 <result_summary> 说明原因
4. 结果摘要要简洁明确，2-3句话概括关键发现/完成的工作

## 结果摘要格式（必须在回复末尾包含）
<result_summary>
[用2-3句话概括你完成了什么、得到了什么关键结果]
</result_summary>
```