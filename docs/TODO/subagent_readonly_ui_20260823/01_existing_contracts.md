# 现有接口与边界

## 1. 当前基线

本设计基于 Operit `development` 的提交 `30d6c0124`。工作区在编写本文档前干净，上一轮 Chat View Slot 已推送到 `origin/development`。

当前插件体系已经包含：

- Chat View 生命周期 Hook：打开、更新、关闭聊天视图
- Chat Input Hook：输入变化、提交前拦截、提交完成
- Chat Message Hook：消息持久化通知
- Tool Lifecycle Hook：工具请求、权限检查、开始、结果、错误、结束
- Prompt、Summary 和 XML 渲染 Hook
- Chat View Slot：输入区内的宿主渲染位置

## 2. Chat View Slot 契约

宿主常量位于 [`ChatViewSlotPluginRegistry.kt`](../../../app/src/main/java/com/ai/assistance/operit/plugins/chatview/ChatViewSlotPluginRegistry.kt)。当前插槽只有：

| 插槽 | 宿主位置 |
| --- | --- |
| `above_input` | 聊天输入控件上方 |
| `input_drawer` | 输入容器内、可编辑文本框上方 |
| `input_toolbar_right` | 模型选择器右侧的输入工具栏 |

宿主传给 Kotlin 插件的 `ChatViewSlotRenderParams` 包括：

- `context: Context`
- `slot: String`
- `chatId: String?`
- `runtime: String`
- `inputStyle: String`
- `isProcessing: Boolean`
- `isInputFocused: Boolean`
- `inputText: String`

宿主渲染结果是 `ChatViewSlotRenderResult`：

- `Text(text)`：由宿主渲染为普通文本
- `ComposeDslScreen(...)`：由宿主的 Compose DSL runtime 渲染

`ChatViewSlotPluginRegistry.RenderSlot` 会根据输入文本、焦点和处理状态重新解析插件，因此 Slot renderer 必须是轻量的声明式函数。

## 3. ToolPkg 注册契约

ToolPkg 侧通过以下 API 注册：

```ts
ToolPkg.registerChatViewSlotPlugin({
  id: string,
  slot: ToolPkg.ChatViewSlot | string,
  function: (event: ToolPkg.ChatViewSlotHookEvent) => ToolPkg.ChatViewSlotRenderReturn
})
```

也支持全局函数：

```ts
registerToolPkgChatViewSlotPlugin(definition)
```

注册函数经过以下链路：

1. `JsToolPkgRegistration` 将 JSON 注册项放入 `CHAT_VIEW_SLOT` bucket。
2. `JsEngine.registerToolPkgChatViewSlotPlugin` 接收 Native bridge 调用。
3. `ToolPkgMainRegistrationScriptParser` 校验 `id`、`slot`、`function`。
4. `ToolPkgParser` 将注册项转换为 `ToolPkgSlotFunctionHookRuntime`。
5. `ToolPkgChatViewSlotBridge` 监听已启用 ToolPkg，并调用 `runToolPkgMainHook`。
6. `ChatViewSlotPluginRegistry` 在输入区对应插槽中渲染结果。

事件常量为 `toolpkg_chat_view_slot`，事件名固定为 `render`。payload 为：

- `slot?`
- `chatId?`
- `runtime?`
- `inputStyle?`
- `isProcessing?`
- `isInputFocused?`
- `inputText?`

Slot Bridge 已接入当前 `ToolPkgHookExecutionBudget`，不会为每次输入变化绕过插件 Hook 的超时约束。

## 4. 为什么不能复用 Slot

`ChatViewSlot` 的宿主位置属于输入区，而子代理卡片应出现在聊天消息流中。复用 Slot 会产生三个问题：

1. 卡片无法稳定挂到对应的父消息或 AI 回合。
2. 输入区重组和消息历史滚动的生命周期不同。
3. Slot 的 Compose DSL 具备交互、导航和工具调用能力，不能作为“只读详情”安全边界。

子代理应在 [`ChatArea.kt`](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/ChatArea.kt) 的消息列表中由宿主插入，而不是放在输入区。

## 5. 当前数据缺口

### 5.1 消息锚点

[`MessageEntity.kt`](../../../app/src/main/java/com/ai/assistance/operit/data/model/MessageEntity.kt) 已有数据库主键 `messageId`，但 [`ChatMessage.kt`](../../../app/src/main/java/com/ai/assistance/operit/data/model/ChatMessage.kt) 没有携带这个字段。当前 UI 主要使用 `timestamp`，并在 `ChatArea` 中对相同 timestamp 做 occurrence 处理。

子代理卡片需要一个稳定的 `parentMessageId` 或等价 anchor。不能以文本内容、工具名称或时间戳单独推断父消息。

### 5.2 工具调用身份

[`AITool.kt`](../../../app/src/main/java/com/ai/assistance/operit/data/model/AITool.kt) 当前只有工具名、参数和原始文本，没有 `callId` 或 `executionId`。现有 Tool Lifecycle payload 也没有子代理执行上下文。

因此不能从现有 `ToolProgressBus` 的全局单条进度状态恢复一个可靠的子代理工具调用时间线。必须在 Agent/tool loop 中显式创建并传递执行上下文。

### 5.3 消息持久化 Hook 的职责

`ChatMessageHook` 是消息落库后的通知，不是消息渲染扩展点。它可以用于同步摘要或外部索引，但不适合作为子代理卡片的主要数据来源。

## 6. 当前阶段明确不做

- 不移植旧分支的 Agent registration 代码
- 不把 `agentId` 仅塞进一次 `SendMessageOptions`
- 不用 Prompt 文本中的标签猜测子代理
- 不用普通聊天页承载子代理详情
- 不允许插件通过 Compose DSL 自己声明一个“看起来只读”的页面来代替宿主约束
