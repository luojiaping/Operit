# 插件 API 与只读 UI

## 1. API 定位

新接口建议命名为 `ChatSubagentViewPlugin`，ToolPkg 注册名建议为：

```ts
ToolPkg.registerChatSubagentViewPlugin(...)
```

它与 `ToolPkg.registerChatViewSlotPlugin` 是并列接口：

- `ChatViewSlot`：输入区宿主插槽
- `ChatSubagentViewPlugin`：聊天消息流中的子代理卡片

不要把两个接口合并成一个可以任意渲染的 Compose DSL API。

## 2. ToolPkg 类型草案

以下是设计草案，不是当前已发布类型：

```ts
export type SubagentExecutionStatus =
  | "queued"
  | "running"
  | "completed"
  | "failed"
  | "cancelled";

export interface SubagentExecutionSnapshot extends JsonObject {
  executionId: string;
  parentChatId: string;
  parentMessageId?: string;
  parentExecutionId?: string;
  childChatId?: string;
  agentId: string;
  agentDisplayName: string;
  agentMode?: string;
  prompt: string;
  status: SubagentExecutionStatus;
  summary?: string;
  errorMessage?: string;
  toolCallCount: number;
  createdAt: number;
  startedAt?: number;
  finishedAt?: number;
  updatedAt: number;
}

export interface ChatSubagentViewEventPayload extends JsonObject {
  action: "render";
  execution: SubagentExecutionSnapshot;
}

export interface ChatSubagentCardDefinition extends JsonObject {
  title?: string;
  subtitle?: string;
  summary?: string;
  icon?: string;
  accentColor?: string;
  order?: number;
}

export type ChatSubagentViewReturn =
  | ChatSubagentCardDefinition
  | null
  | void
  | Promise<ChatSubagentCardDefinition | null | void>;

export interface ChatSubagentViewPluginRegistration {
  id: string;
  function: (
    event: ChatSubagentViewHookEvent
  ) => ChatSubagentViewReturn;
}
```

`executionId` 是详情打开的唯一键。插件返回值不能包含 `composeDsl`、`sendMessage`、`callTool` 或自定义点击回调。

## 3. Kotlin 宿主接口草案

```kotlin
data class ChatSubagentRenderParams(
    val context: Context,
    val execution: SubagentExecutionSnapshot
)

interface ChatSubagentViewPlugin {
    val id: String
    fun supports(execution: SubagentExecutionSnapshot): Boolean
    suspend fun resolveCard(
        params: ChatSubagentRenderParams
    ): ChatSubagentCardDefinition?
}
```

Registry 负责：

- 注册、注销和变更通知
- 监听当前聊天的 execution snapshots
- 按 `parentMessageId` 分组
- 调用插件并应用 Hook 超时预算
- 在消息列表中渲染宿主固定卡片
- 处理卡片点击并打开只读详情

## 4. 消息流中的位置

`ChatArea` 当前在每个 `MessageItem` 后插入间距。建议改成：

```text
MessageItem(parent message)
SubagentCard(executions anchored to parent message)
Spacer
```

卡片的排列顺序使用 `createdAt` 和 `sequence`，不能依赖 ToolPkg 注册顺序。卡片应同时适配 Cursor 和 Bubble 两种消息风格，避免把卡片实现复制到两套 AI 消息组件。

## 5. 固定只读详情页

建议新增 `SubagentReadonlyDetailScreen` 或固定只读 Bottom Sheet，由宿主直接读取：

```kotlin
SubagentExecutionStore.observeExecution(executionId)
```

详情内容：

1. Agent 名称、ID、状态和时间
2. 子代理任务 Prompt
3. 当前摘要或最终摘要
4. 工具调用时间线
5. 每个工具调用的名称、参数、状态、结果和错误

详情内容允许折叠、滚动和返回。是否允许复制文本可以作为单独的只读操作，不等同于发送或执行。

详情页禁止：

- `ChatInputBottomBar`
- `sendMessage`
- 编辑父消息
- 重试或继续执行
- 工具调用按钮
- 进入带有输入框的普通聊天页面
- 由插件提供可执行 Compose DSL

## 6. 只读边界的实际保证

“页面没有输入框”只能保证宿主 UI 交互不能发送消息，不能限制恶意插件自身调用其它 ToolPkg API。因此边界分两层：

### 宿主 UI 边界

只读详情由原生宿主渲染，插件不能替换页面内容，也不能注入交互动作。

### Agent/插件执行边界

未来 Agent runtime 仍需使用独立的 Agent 权限、工具集合和执行授权。子代理详情页不应成为绕过 Agent 权限的入口。

## 7. 不采用的方案

### 直接复用普通子聊天

普通聊天页自带输入框、重试、编辑和消息发送路径，不能满足只读要求。

### 用 XML 或文本标签解析子代理

文本解析无法稳定关联 execution、tool call 和父消息，流式更新与历史恢复都会出错。

### 让插件返回任意 Compose DSL

当前 Compose DSL 类型包含 `callTool`、`navigate` 等能力，详情只读要求无法靠约定保证。
