# Operit 子代理系统架构设计方案

> 版本: v1.0 | 日期: 2026-05-30

---

## 一、设计概览

### 目标
在 Operit 中实现类似 Claude Code Subagents / Cursor Parallel Agents 的子代理系统，让主代理可以派发子任务给独立代理实例并行执行，用户可以透明地观察每个子代理的执行过程。

### 核心决策
1. **子代理面板嵌入主代理消息下方**，主代理等待所有子代理完成后才继续
2. **对话数据永久保留**，UI 层面按轮次折叠管理
3. **子代理名称自动生成**，基于任务内容智能命名

---

## 二、数据模型

### 2.1 SubAgentSession (ObjectBox Entity)

```
位置: data/model/SubAgentSession.kt

data class SubAgentSession(
    @Id var id: Long = 0,
    val agentId: String,                // UUID
    val parentChatId: String,           // 主对话 chatId
    val parentMessageTimestamp: Long,   // 触发该子代理的主代理消息时间戳
    val generationId: Int,              // 第几代（主代理每发一轮消息 generationId++）
    val name: String,                   // 自动生成的名称（如 "架构分析"）
    val task: String,                   // 任务描述
    val status: SubAgentStatus,         // PENDING / RUNNING / COMPLETED / FAILED / CANCELLED
    val createdAt: Long,
    val completedAt: Long? = null,
    val resultSummary: String? = null,  // 执行完毕后的结果摘要
    val errorMessage: String? = null,
    val toolsWhitelist: String? = null, // JSON 序列化的工具白名单，null=默认白名单
    val modelConfigId: String? = null,  // 使用的模型配置 ID，null=继承主代理
    val maxRounds: Int = 10,            // 最大工具调用轮次
)

enum class SubAgentStatus {
    PENDING,      // 已创建，等待执行
    RUNNING,      // 执行中
    COMPLETED,    // 正常完成
    FAILED,       // 执行失败
    CANCELLED     // 被取消
}
```

### 2.2 SubAgentGeneration (运行时聚合，不持久化)

```
位置: api/chat/subagent/SubAgentGeneration.kt

data class SubAgentGeneration(
    val generationId: Int,
    val parentChatId: String,
    val parentMessageTimestamp: Long,
    val agents: List<SubAgentCardState>,
    val allCompleted: Boolean
)

data class SubAgentCardState(
    val agentId: String,
    val name: String,
    val task: String,
    val status: SubAgentStatus,
    val elapsedTimeMs: Long,
    val resultSummary: String?,
    val currentStep: String?,           // 当前正在执行的步骤描述
    val chatId: String                  // 子代理的独立 chatId（点击查看详情用）
)
```

### 2.3 SubAgentToolResultData (工具返回值)

```
位置: core/tools/ToolResultDataClasses.kt (新增)

data class SubAgentResultData(
    val agentId: String,
    val agentName: String,
    val task: String,
    val success: Boolean,
    val resultSummary: String,
    val elapsedTimeMs: Long,
    val roundsUsed: Int,
    val chatId: String
) : ToolResultData {
    override fun toDisplayString(): String = resultSummary
}
```

---

## 三、核心组件

### 3.1 SubAgentManager (管理器)

```
位置: api/chat/subagent/SubAgentManager.kt

职责:
- 管理所有子代理实例的生命周期
- 提供创建、执行、取消、查询接口
- 通过 StateFlow 向 UI 层暴露实时状态
- 负责自动生成子代理名称

class SubAgentManager(private val context: Context) {

    // 所有子代理会话状态 (agentId -> 状态)
    private val _agentStates = MutableStateFlow<Map<String, SubAgentCardState>>(emptyMap())
    val agentStates: StateFlow<Map<String, SubAgentCardState>> = _agentStates.asStateFlow()

    // 按 generation 分组的状态
    private val _generationStates = MutableStateFlow<List<SubAgentGeneration>>(emptyList())
    val generationStates: StateFlow<List<SubAgentGeneration>> = _generationStates.asStateFlow()

    /**
     * 创建并执行一个子代理
     * @return SubAgentCardState（完成后包含结果）
     */
    suspend fun spawnAndExecute(
        parentChatId: String,
        parentMessageTimestamp: Long,
        generationId: Int,
        task: String,
        toolsWhitelist: List<String>? = null,
        modelConfigId: String? = null,
        maxRounds: Int = 10
    ): SubAgentResultData {
        // 1. 生成 agentId 和 chatId
        // 2. 自动生成名称（调用 generateAgentName）
        // 3. 创建 SubAgentSession 并持久化
        // 4. 更新 StateFlow（UI 收到通知，显示卡片）
        // 5. 创建 EnhancedAIService.getChatInstance(chatId)
        // 6. 调用 sendMessage(isSubTask=true, stream=false)
        // 7. 实时更新卡片状态（RUNNING/进度/步骤描述）
        // 8. 完成后更新 StateFlow（UI 收到通知，卡片变绿）
        // 9. 返回 SubAgentResultData
    }

    /**
     * 并行执行多个子代理
     */
    suspend fun spawnAndExecuteParallel(
        parentChatId: String,
        parentMessageTimestamp: Long,
        generationId: Int,
        tasks: List<SubAgentTask>
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
                        maxRounds = task.maxRounds
                    )
                }
            }.awaitAll()
        }
    }

    /**
     * 取消子代理
     */
    suspend fun cancelAgent(agentId: String) { ... }

    /**
     * 获取指定 parentChatId 的所有子代理（用于 UI 展示）
     */
    fun getAgentsForChat(chatId: String): Flow<List<SubAgentGeneration>> { ... }
}
```

### 3.2 AgentNameGenerator (名称生成器)

```
位置: api/chat/subagent/AgentNameGenerator.kt

职责: 根据任务描述智能生成简短的代理名称

策略（按优先级）:
1. 如果任务 ≤4个中文字/词，直接使用任务文本
2. 从任务中提取关键动词+名词（如 "分析项目架构" → "架构分析"）
3. 使用 LLM 生成（通过 getAIServiceForFunction(SUMMARY) 用一句话概括）
4. 兜底: "子任务-{序号}"

object AgentNameGenerator {
    /**
     * 快速提取名称（不调用 LLM，纯规则）
     */
    fun extractQuickName(task: String): String? {
        // 1. 如果 task ≤ 8 字符，直接返回
        // 2. 提取关键词（去掉 "帮我"、"请"、"分析一下" 等噪音词）
        // 3. 取前 6 个字符 + "..."
    }

    /**
     * 使用 LLM 生成智能名称
     * 调用 ConversationService 的摘要能力
     */
    suspend fun generateSmartName(
        task: String,
        multiServiceManager: MultiServiceManager
    ): String {
        val prompt = "请用1-4个中文字概括以下任务的核心目标（不要标点符号，不要解释）：\n$task"
        val result = // 调用 SUMMARY 功能类型的 AIService
        return result.take(8)
    }
}
```

### 3.3 SubAgentExecutor (执行器)

```
位置: api/chat/subagent/SubAgentExecutor.kt

职责: 负责子代理的实际对话执行，包括工具过滤

class SubAgentExecutor(private val context: Context) {

    /**
     * 为子代理构建专用的 System Prompt
     */
    fun buildSubAgentSystemPrompt(
        task: String,
        toolsWhitelist: List<String>?
    ): String {
        return """
        你是一个任务执行子代理。你的唯一目标是完成以下任务：
        
        ## 任务
        $task
        
        ## 规则
        1. 专注于完成任务，不要偏离主题
        2. 完成后在回复的最后使用 <result_summary> 标签包裹结果摘要
        3. 如果遇到无法解决的问题，使用 <result_summary> 说明原因
        4. 不要主动与用户对话，你的输出只面向主代理
        
        ## 结果摘要格式
        <result_summary>
        [用2-3句话概括你完成了什么、得到了什么结果]
        </result_summary>
        """.trimIndent()
    }

    /**
     * 执行子代理对话
     * 使用 EnhancedAIService 的 sendMessage，设置 isSubTask=true
     */
    suspend fun execute(
        chatId: String,
        task: String,
        toolsWhitelist: List<String>?,
        modelConfigId: String?,
        maxRounds: Int,
        onStatusUpdate: (SubAgentStatus, String?) -> Unit  // 状态回调
    ): SubAgentExecutionResult {
        val service = EnhancedAIService.getChatInstance(context, chatId)
        // 设置自定义 system prompt
        // 调用 sendMessage(isSubTask=true, stream=false)
        // 解析结果中的 <result_summary>
        // 返回结果
    }
}
```

---

## 四、工具注册

### 4.1 spawn_subagent 工具

```
位置: core/tools/ToolRegistration.kt (新增注册)
实现: core/tools/defaultTool/standard/StandardSubAgentTools.kt (新文件)

工具名: spawn_subagent
描述: 创建一个子代理来独立执行指定任务。子代理拥有自己的对话历史和工具集。
      主代理会等待子代理完成后获得结果。

参数:
- task (required): 任务描述，要让子代理完成的具体任务
- tools_whitelist (optional): JSON 数组，指定子代理可用的工具列表。
                               不传则使用默认安全工具集（只读 + 终端）
- max_rounds (optional): 最大工具调用轮次，默认 10
- model_config_id (optional): 模型配置 ID，不传则继承主代理的配置
```

### 4.2 spawn_subagents_parallel 工具

```
工具名: spawn_subagents_parallel
描述: 并行创建多个子代理执行不同任务。所有子代理完成后返回结果。

参数:
- tasks (required): JSON 数组，每个元素包含 task、tools_whitelist、max_rounds
  示例: [{"task": "分析架构", "max_rounds": 5}, {"task": "扫描依赖", "max_rounds": 3}]
```

### 4.3 子代理默认工具白名单

```
安全工具集（默认）:
- 只读: read_file, read_file_part, list_files, find_files, file_info, grep_code, grep_context
- 计算: calculate
- 网页: visit_web, download_file
- 终端: execute_command (只读命令)
- 信息: get_device_info

完整工具集（需主代理显式授权 tools: "all"）:
- 所有标准工具
```

---

## 五、核心流程改造

### 5.1 EnhancedAIService 改造

#### 新增字段
```kotlin
// EnhancedAIService 中新增
private val subAgentManager = SubAgentManager(context)
```

#### processStreamCompletion 改造

在 `processStreamCompletion` 的工具调用检测分支中：

```kotlin
// 现有逻辑:
if (extractedToolInvocations.isNotEmpty()) {
    handleToolInvocation(...)
}

// 改造为:
if (extractedToolInvocations.isNotEmpty()) {
    val subAgentInvocations = extractedToolInvocations.filter { 
        it.tool.name in setOf("spawn_subagent", "spawn_subagents_parallel") 
    }
    val normalInvocations = extractedToolInvocations.filter { 
        it.tool.name !in setOf("spawn_subagent", "spawn_subagents_parallel") 
    }
    
    // 先执行普通工具
    if (normalInvocations.isNotEmpty()) {
        handleToolInvocation(normalInvocations, ...)
    }
    
    // 再执行子代理工具（会阻塞等待完成）
    if (subAgentInvocations.isNotEmpty()) {
        handleSubAgentInvocation(subAgentInvocations, context, ...)
    }
}
```

#### 新增 handleSubAgentInvocation 方法

```kotlin
private suspend fun handleSubAgentInvocation(
    invocations: List<ToolInvocation>,
    context: MessageExecutionContext,
    ...
) {
    // 1. 解析 spawn_subagent / spawn_subagents_parallel 的参数
    // 2. 调用 subAgentManager.spawnAndExecute / spawnAndExecuteParallel
    // 3. 等待所有子代理完成（此时 UI 显示进度卡片）
    // 4. 将结果包装为 ToolResult（包含 SubAgentResultData）
    // 5. 调用 processToolResults 继续对话循环
}
```

### 5.2 对话循环不变

**关键设计**: 子代理工具的执行对 `processStreamCompletion` 来说，就是一个普通的工具调用。
区别在于这个工具执行时间可能很长（几秒到几分钟），但逻辑完全一致：

```
主代理输出包含 spawn_subagent → 
  handleToolInvocation → 
    执行子代理（阻塞等待） → 
    返回 ToolResult → 
  processToolResults → 
    再次调用 LLM → 
    LLM 基于子代理结果继续输出
```

这样**不需要修改对话循环的核心逻辑**，只需要：
1. 在 `ToolExecutionManager.executeInvocations` 中支持长时间运行的工具
2. 在工具执行期间通过 StateFlow 向 UI 推送进度

---

## 六、UI 组件

### 6.1 SubAgentPanelView (子代理面板 Composable)

```
位置: ui/features/chat/components/subagent/SubAgentPanelView.kt

@Composable
fun SubAgentPanelView(
    generations: List<SubAgentGeneration>,
    onAgentClick: (SubAgentCardState) -> Unit  // 点击卡片打开详情
) {
    // 当前代（展开）
    val currentGen = generations.lastOrNull()
    // 历史代（折叠）
    val historicalGens = generations.dropLast(1).reversed()

    Column {
        // 当前代 - 展开显示
        if (currentGen != null) {
            SubAgentGenerationView(
                generation = currentGen,
                isExpanded = true,
                onAgentClick = onAgentClick
            )
        }

        // 历史代 - 折叠显示
        if (historicalGens.isNotEmpty()) {
            var showHistory by remember { mutableStateOf(false) }
            
            TextButton(onClick = { showHistory = !showHistory }) {
                Text("历史 (${historicalGens.size} 代, ${historicalGens.sumOf { it.agents.size }} 个)")
            }
            
            if (showHistory) {
                historicalGens.forEach { gen ->
                    SubAgentGenerationView(
                        generation = gen,
                        isExpanded = false,
                        onAgentClick = onAgentClick
                    )
                }
            }
        }
    }
}
```

### 6.2 SubAgentCardView (单个子代理卡片)

```
位置: ui/features/chat/components/subagent/SubAgentCardView.kt

@Composable
fun SubAgentCardView(
    agent: SubAgentCardState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = when (agent.status) {
                SubAgentStatus.RUNNING -> MaterialTheme.colorScheme.primaryContainer
                SubAgentStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                SubAgentStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                SubAgentStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
                SubAgentStatus.CANCELLED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            SubAgentStatusIcon(agent.status)
            
            Spacer(Modifier.width(8.dp))
            
            Column(Modifier.weight(1f)) {
                // 名称
                Text(agent.name, style = MaterialTheme.typography.titleSmall)
                
                // 状态描述
                Text(
                    when (agent.status) {
                        SubAgentStatus.PENDING -> "等待中..."
                        SubAgentStatus.RUNNING -> agent.currentStep ?: "执行中..."
                        SubAgentStatus.COMPLETED -> agent.resultSummary?.take(50) ?: "已完成"
                        SubAgentStatus.FAILED -> agent.resultSummary?.take(50) ?: "失败"
                        SubAgentStatus.CANCELLED -> "已取消"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // 耗时
            Text(
                formatElapsedTime(agent.elapsedTimeMs),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
```

### 6.3 SubAgentDetailSheet (详情底部弹窗)

```
位置: ui/features/chat/components/subagent/SubAgentDetailSheet.kt

当用户点击子代理卡片时弹出，展示该子代理的完整对话历史。

@Composable
fun SubAgentDetailSheet(
    agentState: SubAgentCardState,
    chatHistory: List<ChatMessage>,  // 从 ObjectBox 加载
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // 顶部：代理名称 + 状态 + 耗时
        SubAgentDetailHeader(agentState)
        
        // 中间：完整对话历史（复用主对话的 ChatMessage 渲染组件）
        LazyColumn {
            items(chatHistory) { message ->
                // 复用 BubbleStyleChatMessage 或 CursorStyleChatMessage
            }
        }
    }
}
```

### 6.4 嵌入主对话消息

在主对话的消息列表中，当一条主代理消息关联了子代理时：

```
位置: ui/features/chat/components/style/bubble/BubbleStyleChatMessage.kt (修改)

在 AI 消息气泡下方，如果该消息有子代理，显示 SubAgentPanelView：

@Composable
fun BubbleStyleChatMessage(message: ChatMessage, ...) {
    Column {
        // 原有的气泡内容
        MessageBubbleContent(message, ...)
        
        // 如果这条消息关联了子代理
        val agentGenerations by subAgentManager
            .getAgentsForChat(message.chatId)
            .collectAsState(initial = emptyList())
        
        val relatedGens = agentGenerations.filter { 
            it.parentMessageTimestamp == message.timestamp 
        }
        
        if (relatedGens.isNotEmpty()) {
            SubAgentPanelView(
                generations = relatedGens,
                onAgentClick = { agentState -> 
                    // 打开 SubAgentDetailSheet
                }
            )
        }
    }
}
```

---

## 七、与 Prompt 系统集成

### 7.1 工具提示词 (SystemToolPrompts.kt)

```kotlin
// 新增
ToolPrompt(
    name = "spawn_subagent",
    description = "创建一个子代理来独立执行指定任务。子代理拥有独立的对话和工具集，完成后将结果返回给你。" +
                 "适用于需要独立执行的复杂子任务，如文件分析、代码审查、信息收集等。",
    parametersStructured = listOf(
        ToolParameterSchema(
            name = "task",
            type = "string",
            description = "要让子代理完成的任务描述，应足够具体和明确",
            required = true
        ),
        ToolParameterSchema(
            name = "tools_whitelist",
            type = "array",
            description = "子代理可用的工具列表（JSON数组）。不传则使用默认安全工具集。传 \"all\" 允许使用所有工具",
            required = false
        ),
        ToolParameterSchema(
            name = "max_rounds",
            type = "integer",
            description = "最大工具调用轮次，默认10。复杂任务可适当增大",
            required = false
        ),
        ToolParameterSchema(
            name = "model_config_id",
            type = "string",
            description = "模型配置ID，不传则继承主代理配置。可用于为子代理指定更轻量的模型以节省成本",
            required = false
        )
    )
)

ToolPrompt(
    name = "spawn_subagents_parallel",
    description = "并行创建多个子代理同时执行不同任务。所有子代理完成后返回结果。" +
                 "适用于可以独立并行的多个子任务，显著缩短总执行时间。",
    parametersStructured = listOf(
        ToolParameterSchema(
            name = "tasks",
            type = "array",
            description = "任务列表，JSON数组，每个元素包含 task(必填)、tools_whitelist、max_rounds 可选字段",
            required = true
        )
    )
)
```

### 7.2 系统 Prompt 注入

在 SystemPromptConfig 中，当子代理工具可用时，注入使用指导：

```
## 子代理使用指南
当你面对一个可以拆分为独立子任务的复杂任务时，使用 spawn_subagent 或 spawn_subagents_parallel 工具。

适用场景:
- 需要同时分析多个文件/目录
- 需要独立执行的互不依赖的子任务
- 某个子任务需要特殊的工具权限

不适用场景:
- 简单的单步操作
- 子任务之间有强依赖关系
- 任务本身只需要一个工具调用

注意: 子代理执行期间你会处于等待状态，不要向用户输出任何内容。
```

---

## 八、文件清单

### 新增文件

| 文件 | 说明 |
|------|------|
| `data/model/SubAgentSession.kt` | 子代理会话数据模型 |
| `api/chat/subagent/SubAgentManager.kt` | 子代理管理器（生命周期、状态管理） |
| `api/chat/subagent/SubAgentExecutor.kt` | 子代理执行器（实际对话执行） |
| `api/chat/subagent/AgentNameGenerator.kt` | 智能名称生成器 |
| `api/chat/subagent/SubAgentResultData.kt` | 工具返回值数据类 |
| `core/tools/defaultTool/standard/StandardSubAgentTools.kt` | 子代理工具实现 |
| `ui/features/chat/components/subagent/SubAgentPanelView.kt` | 子代理面板 |
| `ui/features/chat/components/subagent/SubAgentCardView.kt` | 子代理卡片 |
| `ui/features/chat/components/subagent/SubAgentGenerationView.kt` | 分代视图 |
| `ui/features/chat/components/subagent/SubAgentDetailSheet.kt` | 详情弹窗 |
| `ui/features/chat/components/subagent/SubAgentStatusIcon.kt` | 状态图标 |

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `core/tools/ToolRegistration.kt` | 注册 spawn_subagent, spawn_subagents_parallel |
| `core/config/SystemToolPrompts.kt` | 添加子代理工具的 Prompt |
| `core/config/SystemToolPromptsInternal.kt` | 添加子代理工具的详细描述（中英文） |
| `api/chat/EnhancedAIService.kt` | 新增 handleSubAgentInvocation, SubAgentManager 实例 |
| `api/chat/enhance/ToolExecutionManager.kt` | 子代理工具的特殊执行路径 |
| `ui/features/chat/components/style/bubble/BubbleStyleChatMessage.kt` | 嵌入子代理面板 |
| `ui/features/chat/components/style/cursor/CursorStyleChatMessage.kt` | 同上 |
| `ui/features/chat/viewmodel/ChatViewModel.kt` | 暴露子代理状态 |

---

## 九、开发计划

### Phase 1: 数据层 + 管理器 (2天)
- [ ] SubAgentSession 数据模型 + ObjectBox Entity
- [ ] SubAgentManager 核心实现
- [ ] AgentNameGenerator 快速提取版
- [ ] SubAgentExecutor 核心执行逻辑

### Phase 2: 工具注册 + 核心改造 (2天)
- [ ] StandardSubAgentTools 实现
- [ ] ToolRegistration 注册
- [ ] SystemToolPrompts 添加
- [ ] EnhancedAIService.handleSubAgentInvocation
- [ ] ToolExecutionManager 子代理执行路径

### Phase 3: UI 组件 (3天)
- [ ] SubAgentCardView
- [ ] SubAgentStatusIcon
- [ ] SubAgentGenerationView
- [ ] SubAgentPanelView
- [ ] SubAgentDetailSheet
- [ ] BubbleStyleChatMessage 嵌入
- [ ] CursorStyleChatMessage 嵌入
- [ ] ChatViewModel 集成

### Phase 4: 调试 + 优化 (1天)
- [ ] 端到端测试
- [ ] AgentNameGenerator LLM 版
- [ ] 性能优化（大量子代理时的 UI 性能）
- [ ] 边界情况处理（取消、超时、错误恢复）

总计: ~8 天

---

## 十、风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 子代理执行时间过长，主代理长时间阻塞 | 用户体验差 | 添加超时机制 + 取消按钮 |
| 多个子代理同时请求 LLM API，触发限流 | 执行失败 | 在 SubAgentManager 中加入并发限制和重试 |
| 子代理消耗大量 token | 成本高 | 默认使用轻量模型 + 工具白名单限制 |
| 子代理名称生成调用 LLM 额外消耗 | 延迟增加 | 先用快速提取，异步用 LLM 优化 |
| UI 层嵌入子代理面板导致消息列表性能下降 | 卡顿 | LazyColumn 复用 + 子代理面板懒加载 |
