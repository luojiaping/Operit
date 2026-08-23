# OpenCode 对照与 Agent 集成边界

## 1. OpenCode 的对应模型

本设计参考 `/root/opencode` 的 `dev` 分支和提交 `9d466cd8497d02db40010077201e07bd10ac33b4`。

关键概念：

- Session 有稳定 ID 和 `parentID`，可以表达 child session 树。
- `task` 工具创建子 session，并把子 session ID 放进工具 metadata。
- 工具调用拥有独立 `callID`。
- Tool part 保存状态、输入、输出、错误和时间。
- UI 根据工具 metadata 找到 child session，再显示子代理卡片和详情。

参考文件：

- [session.ts](https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/session/session.ts)
- [session-v1.ts](https://github.com/anomalyco/opencode/blob/dev/packages/schema/src/v1/session.ts)
- [task.ts](https://github.com/anomalyco/opencode/blob/dev/packages/opencode/src/tool/task.ts)
- [message-part.tsx](https://github.com/anomalyco/opencode/blob/dev/packages/session-ui/src/components/message-part.tsx)
- [session-composer-region.tsx](https://github.com/anomalyco/opencode/blob/dev/packages/app/src/pages/session/composer/session-composer-region.tsx)

## 2. 映射到 Operit

| OpenCode | Operit 设计 |
| --- | --- |
| parent session ID | `parentChatId` / `parentExecutionId` |
| child session ID | `childChatId`，或首期的 `executionId` |
| `ToolPart.callID` | `SubagentToolCallEntity.callId` |
| Tool part state | 工具调用状态、开始/结束时间 |
| task metadata | `agentId`、显示名、任务 Prompt、执行摘要 |
| session history | `SubagentExecutionEntity` + `SubagentToolCallEntity` |
| child session detail page | `SubagentReadonlyDetailScreen` |

不要把 OpenCode 的 child session 直接映射成 Operit 普通 `ChatEntity`，除非后续明确实现独立子聊天存储和只读查看模式。普通 `ChatEntity` 对应的聊天页面有输入和编辑能力，不满足目标边界。

## 3. 与 Plan/Build Agent 的关系

Plan/Build 不是两个 UI 标签，而是两套 Agent 上下文：

- Agent ID 不同
- 专属 system prompt 不同
- 工具可见集合不同
- 工具执行权限不同
- 会话切换和后续轮次不同

子代理执行记录必须保存 qualified Agent ID，而不能只保存卡片标题。未来 Agent runtime 创建子代理时，应同时写入：

```text
executionId
parentExecutionId
agentId
agentMode
prompt
permission context identity
```

工具调用更新必须带着同一个 execution context，不能依赖全局当前工具名。

## 4. Agent runtime 需要提供的生产接口

Agent 执行层最终需要显式调用：

```kotlin
val executionId = subagentStore.startExecution(
    parentChatId = chatId,
    parentMessageId = messageId,
    agentId = qualifiedAgentId,
    prompt = taskPrompt
)

val callId = subagentStore.beginToolCall(
    executionId = executionId,
    toolName = tool.name,
    parametersJson = parametersJson
)
```

工具完成、失败、取消和 Agent 摘要更新都必须使用相同 ID。这样 UI 只观察持久化记录，不需要参与 Agent loop。

## 5. OpenCode 只读差异

OpenCode 的 child session UI 可以隐藏 composer，但服务端的通用 prompt endpoint 不一定自动拒绝 child session prompt。因此 Operit 的只读详情必须更严格：

- 详情页使用独立宿主 screen
- 没有通用 prompt endpoint
- 没有发送回调
- 没有可执行 UI runtime
- 后续若增加 API，也必须在服务端/Agent 层按 execution 权限校验

## 6. 未来插件数据来源

推荐由宿主 Agent runtime 产生真实执行记录，ToolPkg 只注册：

- 哪些 Agent 类型由插件负责展示
- 卡片标题、图标、摘要和颜色
- 状态的本地化展示文案

如果允许 ToolPkg 自己创建任意 execution 记录，插件可以伪造子代理状态，且会混淆权限与审计边界。插件创建子代理应另建受权限控制的 Agent API，不属于本 UI 接口。
