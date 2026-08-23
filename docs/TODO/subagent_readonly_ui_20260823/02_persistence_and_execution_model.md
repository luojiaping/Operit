# 持久化与执行模型

## 1. 目标

子代理卡片必须能在以下状态下稳定工作：

- 子代理正在运行，工具调用不断增加
- 子代理成功结束并有最终摘要
- 子代理失败或被取消
- 用户离开聊天后重新打开
- 应用重启后查看历史记录

因此本设计选择 Room 持久化，而不是只建立内存 `StateFlow`。

## 2. 核心概念

### 2.1 SubagentExecution

一个子代理执行实例表示父 Agent 发起的一次独立子任务。建议字段：

```text
executionId       String  稳定主键，全局唯一
parentChatId      String  父聊天 ID
parentMessageId   Long?   父 AI 消息的稳定主键
parentExecutionId String? 允许子代理继续创建子代理
childChatId       String? 未来若使用独立子聊天时的关联 ID
agentId           String  qualified Agent ID，例如 toolpkg:explore
agentDisplayName  String  当前显示名称快照
agentMode         String  primary/subagent/all 或未来固定模式
prompt            String  子代理收到的任务文本
status            String  queued/running/completed/failed/cancelled
summary           String? 子代理最终摘要或当前摘要
errorMessage      String? 失败信息
createdAt         Long
startedAt         Long?
finishedAt        Long?
updatedAt         Long
```

`agentDisplayName`、`agentMode` 和 prompt 需要保存快照，不能只依赖当前 Agent 配置。Agent 配置后续改变时，历史卡片仍应描述当时的执行。

### 2.2 SubagentToolCall

一个工具调用属于一个 `SubagentExecution`，建议字段：

```text
callId            String  稳定主键
executionId       String  外键
sequence          Int     子代理内的显示顺序
toolName          String
parametersJson    String
status            String  queued/running/completed/failed/cancelled
resultText        String?
errorMessage      String?
startedAt         Long?
finishedAt        Long?
updatedAt         Long
```

参数和结果必须以结构化或可安全展示的字符串保存。UI 可以默认折叠大字段，但不能为了显示摘要而丢掉工具调用身份和状态。

## 3. Room 结构建议

建议新增：

- `SubagentExecutionEntity`
- `SubagentToolCallEntity`
- `SubagentExecutionDao`
- `SubagentExecutionRepository`

`SubagentToolCallEntity.executionId` 建立索引；`SubagentExecutionEntity` 建立以下索引：

- `(parentChatId, parentMessageId)`：消息列表插入卡片
- `(parentChatId, updatedAt)`：当前聊天的增量刷新
- `(status, updatedAt)`：恢复运行中记录和调试

应用数据库当前版本为 21，新增实体需要增加 Room migration 和数据库备份兼容说明，不能使用 destructive migration。

## 4. Store API 建议

宿主 Agent runtime 不应直接操作 DAO。增加一个执行记录 Store，提供窄接口：

```kotlin
interface SubagentExecutionStore {
    suspend fun startExecution(input: SubagentExecutionStart): String
    suspend fun updateExecution(update: SubagentExecutionUpdate)
    suspend fun beginToolCall(input: SubagentToolCallStart): String
    suspend fun completeToolCall(update: SubagentToolCallUpdate)
    fun observeForChat(chatId: String): Flow<List<SubagentExecutionSnapshot>>
    fun observeExecution(executionId: String): Flow<SubagentExecutionDetail>
}
```

Store 对外只提供快照和更新命令。UI 和 ToolPkg 插件不应获得 DAO，也不应能改变执行状态。

## 5. 状态机

允许的主状态流转：

```text
queued -> running -> completed
queued -> running -> failed
queued -> running -> cancelled
queued -> cancelled
```

工具调用状态使用同样的终态规则。重复的完成事件必须按 `callId` 幂等处理，避免流式工具结果或重连导致时间线重复。

## 6. 父消息锚点

推荐把 `messageId: Long?` 加入 `ChatMessage`，从 `MessageEntity.messageId` 映射到 UI。未落库的流式消息可以暂时只存在于 Store 的 pending 状态，待父消息落库后绑定 `parentMessageId`。

如果短期不能扩展 `ChatMessage`，可以由宿主额外传递 `MessageEntity.messageId`，但不应把 `timestamp` 作为长期公开 API。ChatArea 当前用 timestamp 作为 Compose key 的事实不能代替数据库主键。

## 7. 数据体积和隐私

- 卡片只传摘要和工具调用数量，不把所有工具结果塞入消息文本。
- 详情页按 executionId 查询工具调用，分段显示大参数和大结果。
- 需要定义最大单字段长度、超长字段的存储方式和 UI 截断标识。
- 清理父聊天时，子代理记录必须随父聊天删除，或者明确转移到独立历史策略。
- 工具参数和结果可能包含密钥、路径和用户数据，日志中不得复制完整 payload。
