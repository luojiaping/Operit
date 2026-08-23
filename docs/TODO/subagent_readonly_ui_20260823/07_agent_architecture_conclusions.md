---
fork: https://github.com/luojiaping/Operit.git
scope: Operit 插件化 OpenCode Agent 的调研结论、代码依据和总体架构
operit_commit: 2ee241b78
opencode_commit: 9d466cd8497d02db40010077201e07bd10ac33b4
codex_reference: /root/codex/codex-rs
status: design
---

# Pluginized OpenCode Agent Architecture

## 1. 结论摘要

本窗口调研的目标不是把 Codex 当作 Operit 的编码套壳，也不是把 OpenCode 的全部源码直接放进 ToolPkg，而是回答一个更具体的问题：

> 能否把 OpenCode 的权限、独立 Agent、Plan/Build 和子代理 UI 变成 Operit 的可启用插件能力，同时不让 Operit 本体继续膨胀，也不破坏已经发布的角色卡、ToolPkg 和 ChatView 接口？

最终结论是：可以，但必须采用“最小宿主 Agent Kernel + 厚插件产品层”的结构。

```text
ChatView
  -> AgentRouter
      -> LegacyPipeline
          -> role card / character group / existing ToolPkg hooks
      -> AgentPipeline
          -> AgentKernel
              -> opencode_agent ToolPkg
```

宿主只拥有不可绕过的安全和生命周期能力：

- Agent session、run、tool call 的身份
- 历史 projection 和消息 owner
- 结构化模型回合和工具执行
- 权限评估、审批、审计和取消
- 子代理树、Plan/Build 状态和持久化
- UI snapshot、消息锚点和事件 replay

插件拥有 OpenCode 风格的产品语义：

- Agent profile
- system prompt 和规则
- 权限声明
- 工具集合和工具描述
- Plan / Build 文案和计划文件格式
- 子代理类型和 task schema
- Todo、胶囊、卡片和详情展示元数据

这意味着 OpenCode 的“行为”在插件里，Agent 的“执行边界”在宿主里。两者不能反过来。

## 2. 已确认的用户约束

本设计遵循以下已经确认的决策：

- 侧边栏 OpenCode 已经存在，不把现有 WebView 当作新的 Agent 集成方案。
- 目标是革新 Operit 旧的角色卡驱动设计，而不是简单调用 Codex 或 OpenCode CLI。
- 启用 OpenCode Agent 插件后，当前聊天仍复用同一个 ChatView 和聊天持久化。
- Agent 只看到用户消息和自身产生的 Agent 消息。
- Agent 不看到角色卡回复、角色卡开场白、群聊角色上下文和 Operit 原生 summary。
- 旧角色卡链路必须可以继续使用，关闭插件后不改变旧聊天语义。
- 除当前分支最新开发内容外，既有 ToolPkg、ChatView 和插件 API 都视为已发布，不能直接删除或改变语义。
- Plan/Build 切换胶囊的动作由宿主处理，插件只提供展示定义和受控 action 标识。
- 7 个已有设计文件和本目录的 UI 文档属于设计记录，不等于 Agent runtime 已实现。

## 3. 当前工作树与文档范围

本阶段的工作树变更是文档设计，不包含 Agent runtime、Room entity、权限引擎或模型协议的代码实现。

当前文档分工如下：

- [`index.md`](index.md)：跨窗口入口和总体边界
- [`01_existing_contracts.md`](01_existing_contracts.md)：已有 ChatView Slot、ToolPkg Hook 和数据缺口
- [`02_persistence_and_execution_model.md`](02_persistence_and_execution_model.md)：子代理 execution 和 tool call 持久化草案
- [`03_plugin_api_and_readonly_ui.md`](03_plugin_api_and_readonly_ui.md)：子代理卡片和固定只读详情页草案
- [`04_opencode_mapping.md`](04_opencode_mapping.md)：OpenCode child session、ToolPart 和 Operit 记录的映射
- [`05_implementation_plan.md`](05_implementation_plan.md)：子代理只读 UI 的阶段计划
- [`06_opencode_ui_contract.md`](06_opencode_ui_contract.md)：Todo、Plan/Build 胶囊和子代理 UI contract
- 本文件：整个 Agent 架构的调研证据、决策理由和后续边界

`06_opencode_ui_contract.md` 只规定 UI，不替代本文件中的 AgentKernel 和 AgentPipeline 设计。

## 4. Operit 当前实现调查

### 4.1 默认消息链路

当前默认编码能力主要依赖模型在字符串上下文中自行维持状态。

实际链路可以概括为：

```text
ChatView
  -> ChatViewModel
  -> MessageCoordinationDelegate
  -> MessageProcessingDelegate
  -> AIMessageManager
  -> EnhancedAIService
  -> AIService / Provider
  -> XML or provider tool-call parsing
  -> ToolExecutionManager
  -> next full model request
```

核心入口是 [`EnhancedAIService.kt`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt)。

在 [`EnhancedAIService.kt:877`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:877)，一次发送创建自己的执行上下文，但模型回合和工具回合仍由一个大型服务协调。

在 [`EnhancedAIService.kt:1826`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:1826)，模型输出结束后从响应文本提取工具调用。

在 [`EnhancedAIService.kt:2083`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:2083)，工具通过独立的 `toolProcessingScope` 启动。

在 [`EnhancedAIService.kt:2190`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:2190)，多个 `ToolResult` 被拼接成一个工具结果字符串，再提交下一次完整模型请求。

这条链路可以继续服务旧聊天，但它不是 OpenCode 风格的 typed session/turn/tool runtime。

### 4.2 Prompt 结构

内置系统提示模板位于 [`SystemPromptConfig.kt:162`](../../../app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt:162)，默认模板主要由以下占位区段组成：

- 自我介绍
- 工作区规则
- 工具调用说明
- 包系统说明
- 已启用包
- 可用工具

工作区规则位于 [`SystemPromptConfig.kt:519`](../../../app/src/main/java/com/ai/assistance/operit/core/config/SystemPromptConfig.kt:519)，已经包含绝对路径、workspace root 和修改前搜索建议。

但默认设计没有独立的：

- coding agent profile
- plan/build agent identity
- read-only plan permission
- build transition
- subagent depth
- parent/child task state
- verification state
- durable compaction state

因此继续增加 Prompt 文案只能改善模型建议，不会建立真正的执行边界。

### 4.3 PromptTurn 和工具协议

[`PromptTurn.kt`](../../../app/src/main/java/com/ai/assistance/operit/core/chat/hooks/PromptTurn.kt) 只有：

- `SYSTEM`
- `USER`
- `ASSISTANT`
- `TOOL_CALL`
- `TOOL_RESULT`
- `SUMMARY`

每个 turn 主要保存字符串、可选工具名和 metadata。它没有 Agent session、回合 ID、工具调用 ID、权限快照、执行状态或父子关系。

[`AIService.kt:12`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/AIService.kt:12) 的 provider 接口最终返回 `Stream<String>`。

[`AITool.kt:20`](../../../app/src/main/java/com/ai/assistance/operit/data/model/AITool.kt:20) 中的 `ToolInvocation` 只包括工具、原始文本和文本位置，没有稳定 `callId`。

OpenAI Responses 的 structured tool call 在 [`OpenAIProvider.kt:2682`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/llmprovider/OpenAIProvider.kt:2682) 被转换回 XML 内容，再由上层重新解析。

这对旧 Provider 兼容很有价值，但不能作为新 Agent runtime 的内部协议。新 Agent 必须增加 typed model event，旧 XML 只留在 LegacyPipeline。

### 4.4 工具暴露和执行

工具提示由 [`SystemToolPrompts.kt:829`](../../../app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPrompts.kt:829) 等函数根据类别、可见性、工具顺序和 Hook 生成。

当前问题包括：

- FULL 模式可能同时暴露大量与编码无关的工具。
- CLI 模式把工具隐藏到 `search` 和 `proxy` 后面，模型需要额外发现工具。
- 工具并发能力依赖 [`ToolExecutionManager.kt:623`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt:623) 的硬编码工具名集合。
- 工具结果最终仍以字符串和 XML 形式回到模型。
- ToolPkg 工具可以被调用，但没有 Agent profile 级 materialize 机制。

新 AgentKernel 应按 profile 和 mode 构建专用工具集合，而不是把完整工具目录交给模型。

### 4.5 权限问题

当前权限实现位于 [`ToolPermissionSystem.kt`](../../../app/src/main/java/com/ai/assistance/operit/ui/permissions/ToolPermissionSystem.kt)。

高风险事实：

- [`ToolPermissionSystem.kt:82`](../../../app/src/main/java/com/ai/assistance/operit/ui/permissions/ToolPermissionSystem.kt:82) 使用全局单 callback 保存当前权限请求。
- 新请求在 [`ToolPermissionSystem.kt:217`](../../../app/src/main/java/com/ai/assistance/operit/ui/permissions/ToolPermissionSystem.kt:217) 会清理旧请求状态。
- [`ToolExecutionManager.kt:449`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/enhance/ToolExecutionManager.kt:449) 会根据原始调用文本中的 `deny_tool` 标记绕过权限检查。
- [`PathValidator.kt:7`](../../../app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/PathValidator.kt:7) 只校验 Android 路径是否绝对、Linux 路径是否以 `/` 或 `~` 开头，不等于 workspace boundary。
- Tool lifecycle Hook 可以拦截调用，但不能声明 Agent 级权限，也不能安全授予权限。

OpenCode 的 permission 语义更适合作为新内核参考：

```text
action + resource + effect
effect = allow | ask | deny
reply  = once | always | reject
```

插件只能声明规则，宿主计算最终结果。插件不能通过 Prompt 或 Hook 给自身授予权限。

### 4.6 取消和生命周期

[`EnhancedAIService.kt:499`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:499) 创建的 `toolProcessingScope` 没有明确的父 Job。

[`EnhancedAIService.kt:2854`](../../../app/src/main/java/com/ai/assistance/operit/api/chat/EnhancedAIService.kt:2854) 的取消逻辑调用 `cancelChildren()`，但工具 job 需要明确绑定到当前 Agent run 才能保证取消传播。

新内核必须让以下对象拥有明确生命周期：

- Agent session
- Agent run
- model request
- tool call
- permission request
- subagent execution

任何一个对象都不能依赖“当前全局聊天”或“当前工具名”判断归属。

### 4.7 现有插件系统

ToolPkg 已经提供：

- App lifecycle Hook
- Message processing Hook
- Prompt input/history/finalize Hook
- System/tool prompt Hook
- Summary Hook
- Tool lifecycle Hook
- Chat input Hook
- Chat message Hook
- Chat view Hook
- Chat View Slot
- XML renderer
- UI route 和导航入口

注册捕获结构位于 [`JsToolPkgRegistration.kt`](../../../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt)。

现有接口的问题不是缺少扩展点，而是扩展点大部分是：

- 全局注册
- 字符串或 JSON mutation
- 首个匹配插件独占
- 依赖 ToolPkg JS runtime
- 缺少 Agent session/call 身份
- 不能成为权限和执行状态的唯一来源

### 4.8 当前 plan_mode 和 subagent 示例

现有 [`plan_mode_plugin.ts`](../../../examples/plan_mode/src/plugin/plan_mode_plugin.ts) 已证明以下 UI 组合可行：

- 输入菜单 toggle
- Prompt 注入
- 工具列表过滤
- workspace 计划文件
- planask / plantodo XML renderer
- ChatView tracking
- IPC

但其核心状态是 ToolPkg 内存对象，工具限制主要是 Prompt/tool list 过滤，不能作为安全权限控制。

现有 [`subagent.ts`](../../../examples/subagent/src/packages/subagent.ts) 通过 Java bridge 获取全局 `EnhancedAIService`，把 `max_tool_calls` 写进 Prompt，但没有宿主级调用计数和 Agent session。

结论：这两个示例是迁移素材，不是新 Agent runtime 的基础。

## 5. 角色卡隔离结论

### 5.1 现有角色卡进入点

角色卡解析位于 [`MessageCoordinationDelegate.kt:204`](../../../app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt:204) 到 [`MessageCoordinationDelegate.kt:231`](../../../app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt:231)。

角色卡会进一步影响：

- system prompt
- 模型配置
- memory space
- role name/avatar
- group orchestration
- role-scoped history

在 [`MessageCoordinationDelegate.kt:615`](../../../app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt:615) 之后，角色卡已经决定本轮模型配置和 memory profile。

原生 summary 可能在 [`MessageCoordinationDelegate.kt:679`](../../../app/src/main/java/com/ai/assistance/operit/services/core/MessageCoordinationDelegate.kt:679) 启动。

输入内容 Hook 在 [`MessageProcessingDelegate.kt:774`](../../../app/src/main/java/com/ai/assistance/operit/services/core/MessageProcessingDelegate.kt:774) 执行。

Message plugin 到 [`AIMessageManager.kt:422`](../../../app/src/main/java/com/ai/assistance/operit/core/chat/AIMessageManager.kt:422) 才被探测，因此不能作为完整隔离切点。

### 5.2 新的 owner 分流

在角色卡解析之前增加本轮 owner：

```kotlin
sealed interface ChatTurnOwner {
    data object LegacyRoleCard : ChatTurnOwner
    data class PluginAgent(
        val agentId: String,
        val sessionId: String,
    ) : ChatTurnOwner
}
```

当 owner 是 `PluginAgent` 时，宿主必须：

- 跳过角色卡 ID 解析
- 跳过角色卡模型和 memory profile
- 跳过角色卡输入 Hook
- 跳过群聊角色编排
- 跳过角色卡开场白上下文
- 跳过原生 summary
- 跳过原生 memory auto-update
- 使用 Agent profile 和 Agent prompt
- 使用 Agent session 的 compaction

宿主仍然负责：

- 保存用户消息
- 保存 Agent 最终回复
- 更新 ChatView
- 维护取消和 UI 状态
- 统计 Agent provider usage

### 5.3 同一聊天的历史 projection

用户选择的是“同一聊天过滤历史”，不是新建普通 ChatEntity。

UI 可以显示完整聊天记录，但模型上下文使用不同 projection：

```text
Legacy projection
  user messages
  legacy role-card assistant messages
  legacy summary messages

Plugin Agent projection
  user messages
  current plugin-agent assistant messages
  current plugin-agent tool calls/results
  plugin-agent compaction artifact
```

Plugin Agent 必须排除：

- 角色卡开场白
- 角色卡 assistant 消息
- 其他角色群聊消息
- 原生 `sender = summary` 消息
- 其他 Agent session 的消息
- 宿主内部 system metadata

Legacy projection 也应排除 plugin-agent assistant/tool 消息，避免关闭插件后角色卡吸收新 Agent 的工作上下文。

ownership 不能由 `roleName` 判断。当前 [`ChatMessage.kt`](../../../app/src/main/java/com/ai/assistance/operit/data/model/ChatMessage.kt) 只有 sender、roleName 和模型统计字段，没有 message owner。

建议新增内部 owner 维度：

```text
legacy
plugin:toolpkg:opencode_agent
agent:toolpkg:opencode_agent:build
```

owner 必须持久化到消息 metadata 或关联表，不能只存在内存。

### 5.4 原生 summary 的处理

Plugin Agent 不应该继续消费原生 summary，也不应该把 plugin compaction 结果写成无 owner 的普通 summary 消息。

建议：

- Legacy summary 只属于 Legacy projection
- plugin compaction 保存为 session-scoped artifact
- UI 可以显示“上下文已整理”状态，但不把摘要作为普通角色卡消息
- plugin compaction 使用 plugin profile 的 prompt 和模型策略

## 6. OpenCode 调研结论

### 6.1 V1 是当前最值得借鉴的实现

OpenCode V1 的实际 runner 位于 `/root/opencode/packages/opencode/src/session/prompt.ts`。

在 `runLoop` 中已经存在：

- session 状态
- agent profile
- plan/build 提醒
- tool registry
- task/subagent
- compaction
- structured output
- interrupted assistant 收尾
- provider turn 循环

V1 agent profile 位于 `/root/opencode/packages/opencode/src/agent/agent.ts`，包含：

- `name`
- `description`
- `mode`
- `model`
- `prompt`
- `permission`
- `steps`

默认 profile 中，`build` 拥有编辑能力，`plan` 禁止普通编辑，只允许计划文件，`explore` 只开放搜索和读取类工具。

### 6.2 OpenCode 权限语义

OpenCode permission schema 位于 `/root/opencode/packages/schema/src/permission.ts`。

规则形状是：

```ts
type PermissionRule = {
  action: string;
  resource: string;
  effect: "allow" | "ask" | "deny";
};
```

请求包含：

- session ID
- action
- resources
- save patterns
- metadata
- tool message/call source

回复支持：

- `once`
- `always`
- `reject`

这比 Operit 当前按工具名称的全局 `ALLOW/ASK/FORBID` 更适合 Agent profile 和 workspace resource。

### 6.3 OpenCode 子代理

`/root/opencode/packages/opencode/src/tool/task.ts` 的 task tool：

- 检查 parent session
- 计算 parent depth
- 检查 subagent depth limit
- 请求 task permission
- 派生 child permission
- 创建 child session
- 保存 parent/child metadata
- 支持 foreground/background
- 处理取消和结果通知

Operit 当前 `subagent_run` 不具备这些 runtime 能力，不能继续以全局 `EnhancedAIService` 调用模拟。

### 6.4 OpenCode compaction

OpenCode compaction 位于 `/root/opencode/packages/opencode/src/session/compaction.ts`。

它具有：

- token/context limit 触发
- 结构化摘要格式
- prior summary 合并
- head/recent 选择
- tool output 截断
- compaction 专属 agent
- compaction Hook
- auto-continue

Operit 的原生 summary 可以继续服务 LegacyPipeline，但 Plugin Agent 需要 session-scoped compaction。

### 6.5 OpenCode V2 和插件草案的边界

OpenCode 当前 `dev` 的 V2 runner 位于 `/root/opencode/packages/core/src/session/runner/llm.ts`，已经有 typed tool settlement、session history、context epoch 和 compaction，但文件自身仍标记 MCP/plugin/cancellation 等工作未完成。

当前 V2 PluginContext 位于 `/root/opencode/packages/plugin/src/v2/effect/context.ts`，主要是 agent、catalog、command、integration、plugin、reference、skill 等 domain，没有完整 session/tool runtime。

远端 `origin/opencode/swift-nebula@5a026d74ce` 的 `packages/opencode/src/plug` 只是 type-only redesign，README 明确说明尚未接入 application runtime。

因此不能把“移植 OpenCode plugin API”当作完整 Agent 实现。应移植 OpenCode 的行为语义和边界，不直接复制当前未接线的接口草案。

## 7. Codex 调研结论

Codex 仍然是重要的 runtime 参考，但不是本方案的产品入口。

Codex app-server 在 `/root/codex/codex-rs/app-server/README.md` 提供：

- JSON-RPC JSONL
- thread/start 和 thread/resume
- turn/start 和 turn/interrupt
- item started/completed/delta
- command/file approval request
- typed file change、tool call、agent message

Codex 核心在：

- `/root/codex/codex-rs/core/src/session/step_context.rs`
- `/root/codex/codex-rs/core/src/session/world_state.rs`
- `/root/codex/codex-rs/core/src/tools/orchestrator.rs`
- `/root/codex/codex-rs/core/src/tools/parallel.rs`
- `/root/codex/codex-rs/core/src/agent/registry.rs`

它证明了 Agent runtime 必须拥有：

- step-scoped tool/environment/permission snapshot
- 统一 approval、sandbox、network 和执行编排
- 每个工具调用的 cancellation 和 terminal state
- 有限的 child agent 数量和深度
- typed world state 和 context management

但如果把 Codex 作为直接后端，OpenCode 的插件化革新会变成外部 runtime 套壳。因此 Codex 只作为设计和测试参考，必要时保留为独立 runtime，不作为 `opencode_agent` 的内部实现。

## 8. AgentKernel 设计

### 8.1 四种不能混用的 ID

```text
chatId          聊天 UI 和用户消息的持久化根
agentSessionId  一个 Agent 的上下文和权限隔离域
runId           一次用户回合或子任务执行
callId          一次具体工具调用
viewId          UI view 的生命周期 ID
```

`chatId`、`agentSessionId`、`runId`、`callId`、`viewId` 不能互相替代。

### 8.2 核心对象

```ts
AgentSession {
  sessionId: string;
  chatId: string;
  pluginId: string;
  agentId: string;
  profileVersion: string;
  modeId: string;
  parentSessionId?: string;
  depth: number;
  status: "idle" | "running" | "waiting_permission" | "completed" | "failed" | "cancelled";
}

AgentRun {
  runId: string;
  sessionId: string;
  parentMessageId?: string;
  promptSnapshot: string;
  modelSnapshot: JsonObject;
  permissionSnapshot: JsonObject;
  status: string;
}

AgentToolCall {
  callId: string;
  runId: string;
  parentCallId?: string;
  toolName: string;
  argumentsJson: string;
  status: "queued" | "running" | "completed" | "failed" | "cancelled";
  resultText?: string;
  errorMessage?: string;
}
```

公开给 ToolPkg 的 DTO 只保留快照和受控命令，不暴露 Room DAO、Service 实例或内部 coroutine scope。

### 8.3 宿主生命周期

Agent run 的顺序应为：

1. AgentRouter 根据 chat owner 取得或创建 `AgentSession`。
2. 宿主保存本次用户消息和 owner metadata。
3. 宿主根据 Agent session 建立 history projection。
4. 插件提供 profile prompt、rules 和 tool intent。
5. 宿主 materialize 工具并锁定 permission snapshot。
6. Model client 产生文本、reasoning 或 typed tool call。
7. 宿主生成 `callId` 并执行权限检查。
8. 宿主执行工具、保存结果和事件。
9. Agent 继续当前 run，或产生最终回复。
10. 宿主保存 Agent 消息并发布 UI snapshot。

### 8.4 AgentModelClient

现有 `AIService.sendMessage()` 返回 `Stream<String>`，不足以承载新 Agent 协议。

宿主需要新增内部 adapter，至少能产生：

```text
text_delta
reasoning_delta
tool_call(callId, name, arguments)
tool_result(callId, output, error)
usage
completed
cancelled
provider_error
```

Provider structured tool call 可以继续由旧 adapter 转 XML，LegacyPipeline 保持不变。AgentPipeline 应使用 typed adapter，不能再把 XML 作为身份协议。

### 8.5 Tool materialization

每个 profile 在每个 step 只看到自己的工具集合：

- `plan`：read、grep、glob、list、探索型 task、plan file
- `build`：read、grep、glob、edit、write、apply_patch、bash、test、git、task
- `explore`：read、grep、glob、list、受限 bash
- `general`：根据宿主权限和 parent policy 选择工具

模型可见工具和实际执行授权必须使用同一个 Agent permission snapshot。只从 Prompt 中删除工具不构成安全限制。

## 9. Plugin Agent contract

### 9.1 插件拥有的内容

`opencode_agent` ToolPkg 可以声明：

- profile ID 和 qualified Agent ID
- profile mode
- system prompt
- compaction prompt
- tool description 和 input schema
- permission requested rules
- plan 文件格式
- Todo 状态文案
- subagent 类型
- UI card/capsule 展示定义

### 9.2 插件不能拥有的内容

插件不能直接操作：

- `EnhancedAIService`
- `AIService`
- `MessageCoordinationDelegate`
- `MessageProcessingDelegate`
- `AIMessageManager`
- `AIToolHandler`
- `ToolExecutionManager`
- `PackageManager`
- `JsEngine`
- `AppDatabase`、DAO 和 Room entity
- 完整聊天历史
- 原始 API key
- provider 内部 request/response object
- AgentKernel 内部 coroutine scope

现有 `Tools.Chat.sendMessage()` 继续保留给已经发布的 ToolPkg，但不能作为新 Agent loop 的实现方式。

### 9.3 新 API 的版本方式

已发布的 ToolPkg API 保持原语义。新 Agent API 使用独立 capability 和版本：

```text
agent_runtime_v1
agent_ui_v1
```

旧宿主不应加载依赖新 capability 的插件注册项，也不应改变旧消息链路。

建议新增的 Agent facade 只包含窄接口：

```ts
ToolPkg.registerAgentProfile(profile);
ToolPkg.registerAgentTool(tool);
ToolPkg.Agent.attach(request);
ToolPkg.Agent.send(request);
ToolPkg.Agent.spawnChild(request);
ToolPkg.Agent.cancel(runId);
ToolPkg.Agent.onEvent(listener);
```

这些名称是设计草案，必须在阶段 0 冻结后再加入类型声明和注册捕获。

## 10. 权限架构

### 10.1 规则形状

```ts
PermissionRule {
  action: string;
  resource: string;
  effect: "allow" | "ask" | "deny";
}
```

宿主按 action/resource 评估规则，OpenCode 的最后匹配优先语义可以作为实现参考。

### 10.2 权限合成

最终权限至少综合：

- 宿主全局策略
- workspace root 和 environment policy
- Agent profile requested rules
- 当前 mode 的限制
- parent session 的继承限制
- 用户对本次请求的 once/always/reject 回复

插件只能收紧或声明意图，不能把宿主 deny 变成 allow。

### 10.3 Plan 权限

Plan 模式必须在两个位置同时限制：

- tool materialization：不把普通 edit/write/bash 工具暴露给模型
- tool execution：即使模型伪造调用，也由宿主权限引擎拒绝

Plan 文件写权限必须是显式 resource pattern，不能只靠 Prompt 告诉模型“不要编辑”。

### 10.4 审批

权限请求必须按 `sessionId + runId + callId` 管理，不能复用当前全局单 callback。

UI 只显示宿主生成的 permission snapshot：

- action
- resource
- tool name
- parameters preview
- workspace/environment
- requested save patterns

插件不能伪造批准结果，也不能直接调用权限 UI 的内部 callback。

## 11. Plan / Build 架构

Plan 和 Build 是两个 Agent profile 或 mode snapshot，不是两个颜色标签。

### Plan profile

- 只读探索和分析
- 允许写入专属计划文件
- 禁止 workspace 代码修改
- 可以创建只读 explore 子代理
- 可以生成 Todo 和问题清单
- 使用独立 plan prompt 和 compaction prompt

### Build profile

- 读取 Plan artifact
- 允许代码修改
- 允许按权限执行测试、lint 和构建
- 继续保留 plan 上下文和已完成 Todo
- 可以创建受限子代理
- 记录 mode transition 和用户确认

### 切换

```text
Plan mode
  -> host action: request_mode_change(build)
  -> host validates current session and permissions
  -> plugin supplies build profile
  -> next Agent run uses Build snapshot
```

UI 胶囊、Todo 面板和 Plan 详情的 contract 见 [`06_opencode_ui_contract.md`](06_opencode_ui_contract.md)。

## 12. 子代理架构

### 12.1 宿主责任

宿主负责：

- 创建 child Agent session 或 execution
- 保存 parent session、parent run、parent call 关系
- 限制 depth、并发和总数量
- 计算 child permission
- 建立 child history projection
- 传播 cancellation
- 保存 child tool call 和最终摘要
- 向父 Agent 写入结构化 task result

### 12.2 插件责任

插件负责：

- `task` 工具 schema
- `subagent_type` 到 profile 的映射
- child prompt 和规则
- foreground/background 展示文案
- 子代理卡片展示定义

子代理不能通过普通 `ChatEntity` 伪装成独立 session，除非宿主已经提供真正的只读查看和 session ownership；当前 UI 文档明确采用独立 execution record。

### 12.3 子代理 UI

子代理卡片的数据来源必须是 `SubagentExecutionStore` 或未来通用 `AgentExecutionStore`。

不能从以下内容推断卡片状态：

- XML 文本
- 工具名称
- ToolProgressBus 的单条全局状态
- 普通聊天文本
- 插件自己的内存 map

## 13. 持久化设计

当前 AppDatabase 是 21 版，见 [`AppDatabase.kt:20`](../../../app/src/main/java/com/ai/assistance/operit/data/db/AppDatabase.kt:20)。

建议新增或演进为：

- `AgentSessionEntity`
- `AgentRunEntity`
- `AgentToolCallEntity`
- `AgentMessageMetadataEntity` 或 messages owner 字段
- `AgentEventEntity`，如果需要断线 replay

子代理 UI 文档中的 `SubagentExecutionEntity` 和 `SubagentToolCallEntity` 可以作为第一阶段记录表，但名称和关系应保留扩展到 Plan/Build primary Agent 的空间。

必须保存快照而不是只保存配置引用：

- agentId
- agentDisplayName
- profileVersion
- modeId
- prompt snapshot
- model snapshot
- permission context identity
- workspace identity
- plugin version

工具参数和结果需要：

- 最大字段长度
- 大结果的外部文件存储
- UI 截断标记
- 日志脱敏
- 删除父聊天时的关联策略
- 重复完成事件按 callId 幂等

## 14. UI 设计与 runtime 的关系

UI 设计不能成为 Agent runtime 的替代品。

当前 7 个子代理 UI 文档解决的是：

- execution 记录如何保存
- tool call 如何显示
- 父消息如何锚定卡片
- 详情页如何保持只读
- 插件如何提供展示 metadata

尚未解决的是：

- Agent session 创建
- Agent profile 注册
- 权限 materialize
- typed model tool call
- Plan/Build 状态机
- 子代理真正创建和取消
- history projection
- compaction

因此实现顺序必须是：

```text
Agent contract
  -> persistence
  -> AgentKernel
  -> plugin profiles
  -> UI snapshot and cards
```

不能先做漂亮卡片，再用 Prompt 文本猜测状态。

## 15. 方案比较

### 15.1 继续只改 Prompt

优点：改动小。

缺点：

- 没有权限边界
- 没有 session identity
- 没有恢复
- 没有稳定 tool call
- 没有 Plan/Build 强制约束
- 角色卡和 summary 仍然会进入上下文

结论：只能作为 profile prompt 的一部分，不能作为架构方案。

### 15.2 直接移植 OpenCode plugin API

优点：概念名称接近 OpenCode。

缺点：

- OpenCode 当前 V2 plugin context 还不是完整 Agent runtime
- 远端 plugin redesign 仍是未接线 type-only sketch
- Operit 的 ToolPkg runtime、Room、Android UI 和权限模型不同
- 只移植 API 类型不会带来 session、tool settlement 和安全边界

结论：借鉴语义，不直接复制 API 表面。

### 15.3 直接套壳 Codex

优点：成熟的 turn、sandbox、approval 和 typed event。

缺点：

- OpenCode Agent 的 profile 和插件产品层被外部 runtime 吞掉
- Operit 旧角色卡和新 Agent 的 ownership 仍需宿主解决
- UI、权限和历史最终仍要做本地适配

结论：作为独立 runtime 或参考实现保留，不作为本方案主路线。

### 15.4 最终方案

采用：

```text
Legacy Chat 保持兼容
  + AgentKernel 作为小型宿主执行内核
  + opencode_agent ToolPkg 承载 OpenCode 产品语义
  + opencode_agent_ui 承载 Todo/Plan/Build/子代理展示
```

## 16. 分阶段实施计划

### 阶段 0：冻结 contract

目标：不改旧 API，只确定新 API。

需要冻结：

- `AgentSession`、`AgentRun`、`AgentToolCall` 的关系
- message owner 和 history projection
- `agentId`、`modeId`、`profileVersion`
- permission rule 和 reply
- typed model event
- `ToolPkg.Agent` facade
- `ChatAgentUi` 和 `ChatSubagentView` 展示 contract
- 纯展示 render 环境

产物：TODO 文档、类型草案、状态机图的 ASCII 版本、错误码表和兼容清单。

### 阶段 1：持久化和消息 owner

目标：即使 Agent runtime 尚未完成，数据结构先能准确表达归属。

范围：

- Room migration
- agent session/run/tool call 表
- parent message stable ID
- message owner metadata
- ChatMessage 和 MessageEntity 映射
- 删除和清理策略
- replay query

不修改旧 ToolPkg payload，不用 roleName 充当 owner。

### 阶段 2：AgentRouter 和 history projection

目标：在角色卡解析之前决定 Legacy 或 Plugin Agent。

范围：

- chat owner state
- plugin agent session attach
- user message shared projection
- legacy/plugin assistant filter
- native summary 隔离
- role input Hook 隔离
- group orchestration 隔离

验收重点：同一聊天切换两种 owner 后，模型上下文不会交叉污染。

### 阶段 3：AgentKernel

目标：提供真正的独立 Agent 执行闭环。

范围：

- typed model client
- step/run loop
- tool materialization
- permission engine
- callId and execution context
- cancellation propagation
- structured tool result
- compaction artifact
- event replay

这一阶段完成后，插件才可以注册真正的 Agent profile。

### 阶段 4：opencode_agent 插件

目标：把 OpenCode 产品语义移出 Operit 本体。

范围：

- plan/build/explore/general profiles
- profile prompt
- requested permission rules
- tool descriptions
- plan file and Todo
- task/subagent profile
- compaction prompt
- mode transition UI

插件不直接访问 Operit 内部 Service。

### 阶段 5：OpenCode UI

目标：使用宿主 snapshot 展示完整 Agent 状态。

范围：

- Todo capsule
- Plan/Build capsule
- Plan detail
- subagent card
- readonly execution detail
- event replay
- host action dispatch

对应文档：[`06_opencode_ui_contract.md`](06_opencode_ui_contract.md)。

### 阶段 6：验证和发布

目标：保证旧系统不变、新系统可控。

验证：

- 旧角色卡聊天行为不变
- 旧 ToolPkg 不需要重打包
- 新 Agent 不看到 legacy assistant/summary
- Plan 不能写 workspace 代码
- Build 的写入必须经过宿主权限
- 子代理 depth 和并发限制生效
- app restart 后 session 和执行记录可查看
- 同一 callId 的重复完成事件不会产生重复记录
- Main/Floating 两个 runtime 不互相取消
- UI 重组不会重复创建 execution

## 17. 绝对不要做的事情

- 不让插件直接调用 `EnhancedAIService` 创建独立 Agent。
- 不让插件用 `Tools.Chat.sendMessage` 递归模拟 Agent loop。
- 不把完整 `PromptTurn` history 直接交给插件。
- 不把 `roleName` 当作消息 owner。
- 不通过 XML 标签推断 session、tool call 或子代理状态。
- 不把 Prompt 中的“不要编辑”当作 Plan 权限。
- 不让子代理普通 ChatEntity 伪装成只读 session。
- 不让只读详情页加载任意 Compose DSL。
- 不让 UI plugin 伪造宿主 execution 状态。
- 不把全局 Tool lifecycle Hook 当作 Agent permission engine。
- 不把现有 ChatView Slot 改成 Agent 专用接口。
- 不为了新 Agent 删除已经发布的 ToolPkg API。
- 不把 OpenCode 未接线的 V2 plugin sketch 当作可运行实现。
- 不在 AgentPipeline 中复用 LegacyPipeline 的角色卡、summary 和全局 memory 状态。

## 18. 验收矩阵

### 18.1 历史隔离

| 场景 | Legacy projection | Plugin Agent projection |
| --- | --- | --- |
| 用户消息 | 可见 | 可见 |
| Legacy role-card assistant | 可见 | 不可见 |
| Legacy opening statement | 可见 | 不可见 |
| Character group assistant | 按旧规则处理 | 不可见 |
| Native summary | 可见 | 不可见 |
| Plugin Agent assistant | 不可见 | 可见 |
| Other Agent assistant | 不可见 | 不可见 |

### 18.2 权限

| 场景 | 期望 |
| --- | --- |
| Plan 调用 workspace edit | 宿主拒绝 |
| Plan 写专属 plan file | 按 resource rule 允许 |
| Build 修改 workspace | 按宿主策略 ask/allow |
| 插件声明 allow 覆盖宿主 deny | 不允许 |
| 两个并发 Agent 请求权限 | 按 request ID 独立处理 |
| 子代理超过 depth | 宿主拒绝创建 |
| 取消父 Agent | 子 Agent 和 tool call 收到取消 |

### 18.3 兼容

| 场景 | 期望 |
| --- | --- |
| 旧 ToolPkg | 继续按旧 API 工作 |
| 旧 ChatView Slot | 位置和行为不变 |
| 未安装 opencode_agent | LegacyPipeline 不变 |
| 关闭 opencode_agent UI | 历史执行记录不被删除 |
| 插件版本变化 | 历史 profile/version 快照仍可解释 |
| App restart | execution/session 状态可查询 |

## 19. 当前文档的完成边界

本文件和 `06_opencode_ui_contract.md` 完成的是研究和设计记录，不表示以下内容已经存在：

- AgentKernel
- AgentRouter
- Agent permission engine
- typed AgentModelClient
- Agent message projection
- Plan/Build runtime
- `ToolPkg.Agent` public API
- Todo/Plan/Build capsule runtime
- SubagentExecution Room implementation

进入代码实现前，应先按照 [`docs/TODO/README.md`](../README.md) 为每个最小功能单元拆分 numbered step，并在每个阶段记录静态检查、迁移验证和真实设备验证结果。

## 20. 决策记录

### 已决定

- 新 Agent 不是 Codex 套壳。
- OpenCode 产品语义放在插件。
- Agent 执行安全边界放在宿主 AgentKernel。
- 同一 ChatView 和聊天持久化继续使用。
- Agent history 使用 owner-aware projection。
- 角色卡、原生 summary 和 plugin Agent 上下文相互隔离。
- 旧已发布 ToolPkg 和 ChatView API 保持兼容。
- Plan/Build action 由宿主处理。
- 子代理 UI 使用宿主固定只读详情。

### 仍需在阶段 0 冻结

- AgentSession、AgentRun、SubagentExecution 的最终命名
- `messageId` 对外使用字符串还是独立 opaque ID
- profile version 和 plugin version 的编码方式
- tool result 的最大持久化大小
- prompt、参数和结果的脱敏规则
- Agent event 是否需要单独持久化
- Plan 文件和 Agent artifact 的清理策略
- UI snapshot 的分页和增量更新协议
