---
fork: https://github.com/luojiaping/Operit.git
scope: Agent Boundary Foundation：Agent contract、执行记录和历史 owner projection
status: in_progress
---

# Agent Boundary Foundation

## 1. 阶段目标

本阶段把 OpenCode 调研得到的 session、run、tool call 和 owner 语义落成 Operit-native 的宿主基础，但不接管当前聊天发送流程。

目标不是复制 OpenCode 的 TypeScript runtime，而是先建立后续 `opencode_agent` 插件可以依赖的稳定身份和数据边界。

当前 LegacyPipeline 保持默认：

```text
ChatView -> ChatServiceCore -> role card -> EnhancedAIService -> existing tools
```

AgentPipeline 目前只拥有 contract、记录表和纯 projection：

```text
Agent contract -> Room records -> owner-aware projection
```

尚未把模型请求、权限弹窗或 ToolPkg 注册接入 AgentPipeline。

## 2. 已完成代码

### 2.1 Agent contract

新增：

- `app/src/main/java/com/ai/assistance/operit/core/agent/contract/AgentIds.kt`
- `app/src/main/java/com/ai/assistance/operit/core/agent/contract/AgentContract.kt`
- `app/src/main/java/com/ai/assistance/operit/core/agent/contract/AgentOwner.kt`
- `app/src/main/java/com/ai/assistance/operit/core/agent/contract/AgentCapabilities.kt`

当前 contract 包含：

- `AgentId`
- `AgentSessionId`
- `AgentRunId`
- `AgentToolCallId`
- `AgentMode`
- `AgentStatus`
- `AgentToolCallStatus`
- `AgentStateTransitions`
- `AgentPermissionRule`
- `AgentProfileDeclaration`
- session/run/tool call start DTO
- session/run/tool call snapshot DTO
- `AgentOwner.LegacyRoleCard`
- `AgentOwner.SharedUser`
- `AgentOwner.PluginAgent`
- `agent_runtime_v1` 和 `agent_ui_v1` capability 常量

ID 使用 opaque value class，插件公开边界后仍使用字符串传输；宿主内部可以使用强类型 ID，避免把 `chatId`、session、run 和 call 混用。

### 2.2 History projection

新增：

`app/src/main/java/com/ai/assistance/operit/core/agent/history/AgentHistoryProjection.kt`

`AgentHistoryProjection` 是纯 Kotlin projector，不依赖 `ChatMessage`、`PromptTurn`、Room 或 UI。

当前定义：

- Legacy projection：保留 shared user 和 legacy owner，排除 plugin agent owner
- Plugin Agent projection：保留 shared user 和指定 session 的 plugin owner，排除 legacy 和其他 agent
- history item 明确区分 user、assistant、summary、tool call、tool result 和 compaction

这一步刻意不修改 `AIMessageManager.getMemoryFromMessages()`，避免在 contract 尚未接入前改变旧角色卡上下文。

### 2.3 Room records

新增：

- `app/src/main/java/com/ai/assistance/operit/data/model/AgentSessionEntity.kt`
- `app/src/main/java/com/ai/assistance/operit/data/model/AgentRunEntity.kt`
- `app/src/main/java/com/ai/assistance/operit/data/model/AgentToolCallEntity.kt`
- `app/src/main/java/com/ai/assistance/operit/data/model/AgentMessageOwnerEntity.kt`
- `app/src/main/java/com/ai/assistance/operit/data/dao/AgentExecutionDao.kt`
- `app/src/main/java/com/ai/assistance/operit/data/repository/AgentExecutionRepository.kt`

新增表：

- `agent_sessions`
- `agent_runs`
- `agent_tool_calls`
- `agent_message_owners`

`agent_message_owners` 是独立关联表，不修改已发布的 `ChatMessage`、`MessageEntity` 和 ToolPkg 消息 payload。没有 owner 记录的旧消息仍然由后续 projection 视为 Legacy 内容。

Repository 只提供 session/run/tool call 的快照和更新命令，不向 UI 或 ToolPkg 暴露 DAO 或 Room Entity。

### 2.4 Database migration

`AppDatabase` 从 21 升到 22，新增 `MIGRATION_21_22` 并注册四张 Agent 表及其索引。

外键关系：

- Agent session -> chat，聊天删除时级联清理 session
- Agent run -> Agent session，session 删除时级联清理 run
- Agent tool call -> Agent run，run 删除时级联清理 tool call
- Agent message owner -> chat/message，消息或聊天删除时清理 owner

本阶段没有修改旧 messages 表，因此旧聊天内容和现有导出格式没有变化。

## 3. 当前没有接入的内容

以下内容明确留在后续阶段：

- AgentRouter
- 角色卡解析之前的 owner 分流
- Legacy/Plugin projection 接入实际模型请求
- AgentModelClient
- typed tool call provider adapter
- Agent permission evaluation
- Tool materialization
- Plan/Build mode transition
- ToolPkg Agent registration capture
- Todo/Plan/Build capsule runtime
- 子代理创建、取消和 foreground/background
- Agent compaction
- UI snapshot bridge

因此当前代码不会改变：

- 角色卡 prompt
- 原生 summary
- 群聊编排
- `EnhancedAIService`
- `AIMessageManager`
- `ConversationService`
- 当前 ToolPkg Hook
- 当前 ChatView Slot
- 当前 ToolPermissionSystem

## 4. 为什么先做这一层

没有稳定 owner 和 execution identity，后续所有 UI 或权限实现都会被迫从以下不稳定信息推断：

- `roleName`
- XML 工具标签
- 工具名称
- 全局 ToolProgressBus
- 当前聊天 ID
- 当前全局 AI service

这些信息无法表达：

- 同一聊天中的多个 Agent session
- Plan 与 Build 的 profile 区别
- 子代理父子关系
- 同一工具多次调用
- 重启后的执行状态
- 角色卡消息和 Agent 消息的隔离

本阶段先把这些身份落成独立 contract 和持久化记录，后续 runtime 才能在不污染旧链路的前提下接入。

## 5. 验收标准

### Contract

- 所有 Agent ID 类型都拒绝空值
- session/run/call ID 可以独立生成
- Agent profile 可以保存 mode、profile version、requested permissions 和 tool IDs
- capability 名称稳定且不复用现有设备 condition

### Projection

- Legacy projection 排除所有 plugin agent item
- Plugin projection 只保留 shared user 和目标 session item
- 其他 agent session 的内容不会进入当前 plugin projection
- summary 不会因为 sender 名称变化而自动进入 plugin projection

### Persistence

- Room migration 21 -> 22 创建全部 Agent 表
- session、run、tool call 使用稳定字符串主键
- parent session、parent run、parent call 可以表达子代理树
- message owner 使用稳定 `messageId` 关联，不使用 timestamp 猜测
- Agent 记录由 Repository 操作，不直接暴露 DAO
- Repository 允许同状态幂等更新，拒绝从终态重新进入运行状态

### Compatibility

- LegacyPipeline 没有新增运行时分支
- 已发布 ToolPkg API 没有修改
- `ChatMessage` 和 `MessageEntity` 的旧字段没有改动
- 当前 UI 没有新增可点击 Agent 行为

## 6. 未运行的验证

遵循当前任务要求，本阶段没有执行构建、测试或 Room migration 运行验证。

后续在允许验证时，至少需要执行：

- Agent contract JVM tests
- Agent projection JVM tests
- Room schema/migration test
- Repository state transition test
- duplicate call completion idempotency test
- parent chat deletion cleanup test

## 7. 下一阶段

下一阶段是 `AgentRouter + owner-aware message projection`：

1. 在角色卡解析前解析 chat owner。
2. 默认保持 Legacy owner。
3. 只有明确绑定 plugin Agent session 的聊天才进入 AgentPipeline。
4. 用户消息仍共享，assistant/summary/tool 内容按 owner 投影。
5. 暂时仍不接权限和模型 provider。

再下一阶段才接入 typed `AgentModelClient`、tool materialization 和 Agent permission engine。
