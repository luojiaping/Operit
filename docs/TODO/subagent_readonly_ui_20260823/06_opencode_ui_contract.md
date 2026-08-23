---
fork: https://github.com/luojiaping/Operit.git
scope: OpenCode Agent 的 Todo、Plan/Build 胶囊与子代理显示 UI 契约
status: design
---

# OpenCode Agent UI Contract

## 1. 文档定位

本文件覆盖 OpenCode 风格 Agent 的 UI 展示接口，重点是：

- Todo / Plan 状态胶囊
- Plan / Build 模式切换胶囊
- 聊天消息流中的子代理卡片
- 子代理卡片对应的宿主只读详情页
- 胶囊和卡片向宿主发送受控动作的方式

本文件是接口设计文档，不代表当前 API 已经实现，也不应直接把草案写入已发布的 `examples/types/*.d.ts`。

当前已有的 `ChatViewSlot` 仍然是输入区扩展接口，位于 [`ChatViewSlotPluginRegistry.kt`](../../../app/src/main/java/com/ai/assistance/operit/plugins/chatview/ChatViewSlotPluginRegistry.kt)。本文件不改变它的注册函数、插槽位置、渲染结果或生命周期。

子代理执行记录、Room 持久化和只读详情的基础设计见：

- [`01_existing_contracts.md`](01_existing_contracts.md)
- [`02_persistence_and_execution_model.md`](02_persistence_and_execution_model.md)
- [`03_plugin_api_and_readonly_ui.md`](03_plugin_api_and_readonly_ui.md)
- [`04_opencode_mapping.md`](04_opencode_mapping.md)
- [`05_implementation_plan.md`](05_implementation_plan.md)

## 2. 目标

### 2.1 Todo / Plan 胶囊

在当前 ChatView 的输入区附近显示当前 Agent 的工作状态：

- 当前是 Plan 还是 Build
- Todo 总数和已完成数量
- 当前进行中的 Todo
- 正在运行的子代理数量
- 是否存在等待用户处理的状态

胶囊只显示当前 Agent session 的快照，不从聊天文本、XML 标签、工具名称或插件注册顺序推断状态。

### 2.2 Plan / Build 切换胶囊

Plan 和 Build 不是普通的主题标签，而是 Agent runtime 的两种上下文状态。UI 胶囊只负责展示当前状态和提出切换请求：

- `plan`：显示只读规划状态
- `build`：显示实施状态
- `request_mode_change`：向宿主提出模式切换请求

点击动作由宿主处理。插件不能直接在点击回调中调用 `sendMessage`、`callTool` 或任意 IPC。

本阶段只规定动作协议，不实际执行 Plan 到 Build 的切换。真正的切换必须由 Agent runtime 校验权限、计划状态和当前回合状态后完成。

### 2.3 子代理卡片

子代理卡片出现在父消息附近，而不是输入区：

```text
ParentMessage
SubagentCard
Spacer
```

卡片至少显示：

- Agent 名称
- Agent 类型或 qualified ID
- 子任务摘要
- 当前状态
- 工具调用数量
- 开始时间和更新时间

点击卡片后由宿主打开固定的只读详情页。插件只能改变卡片的展示元数据，不能替换详情页布局。

## 3. UI 位置

### 3.1 Agent 状态胶囊

Agent 胶囊使用宿主固定区域，不允许插件任意选择 Compose 容器：

| surface | 宿主位置 | 用途 |
| --- | --- | --- |
| `agent_mode_capsule` | 输入工具栏的 Agent 区域 | Plan / Build 当前模式和切换请求 |
| `agent_todo_capsule` | 输入区上方的 Agent 状态区域 | Todo 进度、当前任务和子代理数量 |
| `subagent_card` | 消息流中父消息之后 | 子代理执行摘要和详情入口 |

如果宿主尚未提供前两类固定区域，不能通过改变现有 `input_toolbar_right` 或 `above_input` 的既有语义来临时占用。普通 ToolPkg 仍按已经发布的 `ChatViewSlot` 契约工作。

### 3.2 Todo 详情

Todo 详情由宿主固定渲染，建议使用只读 Bottom Sheet 或固定面板：

- 显示 Todo 的顺序、文本、状态和依赖关系
- 支持滚动和折叠长文本
- 可以返回聊天页面
- 不包含执行按钮
- 不允许插件通过详情页改变 Todo 状态

Todo 状态的写入属于 Agent runtime，不属于本 UI API。

### 3.3 Plan 详情

Plan 详情展示当前 Agent session 的计划快照：

- Plan 标题
- 目标和约束
- 已完成、进行中和待处理事项
- 当前模式
- 最近一次更新时间

计划文件是否存在、内容如何生成以及何时切换到 Build，由 Agent runtime 和 OpenCode Agent 插件共同定义；UI 只消费宿主快照。

### 3.4 子代理详情

子代理详情页沿用 [`03_plugin_api_and_readonly_ui.md`](03_plugin_api_and_readonly_ui.md) 的固定只读边界：

- 没有输入框
- 没有发送按钮
- 没有编辑父消息
- 没有重试或继续执行
- 没有工具执行按钮
- 不加载通用 Compose DSL 页面

## 4. 宿主快照

所有 UI 数据必须由宿主 Agent runtime 生成。插件不能通过读取完整聊天记录自行重建 Agent 状态。

以下是设计级 TypeScript 形状，不是当前公共类型：

```ts
export type AgentTodoStatus =
  | "pending"
  | "in_progress"
  | "completed"
  | "blocked"
  | "cancelled";

export type AgentMode = "plan" | "build";

export interface AgentTodoSnapshot extends ToolPkg.JsonObject {
  id: string;
  text: string;
  status: AgentTodoStatus;
  order: number;
  dependsOn?: string[];
  updatedAt: number;
}

export interface AgentModeCapsuleSnapshot extends ToolPkg.JsonObject {
  agentId: string;
  sessionId: string;
  mode: AgentMode;
  label: string;
  subtitle?: string;
  todoCount: number;
  completedTodoCount: number;
  activeSubagentCount: number;
  waitingForUser: boolean;
  canRequestModeChange: boolean;
  availableModes: AgentMode[];
  updatedAt: number;
}

export interface AgentTodoCapsuleSnapshot extends ToolPkg.JsonObject {
  agentId: string;
  sessionId: string;
  mode: AgentMode;
  todos: AgentTodoSnapshot[];
  currentTodoId?: string;
  activeSubagentCount: number;
  updatedAt: number;
}
```

子代理快照继续使用 [`03_plugin_api_and_readonly_ui.md`](03_plugin_api_and_readonly_ui.md) 中的 `SubagentExecutionSnapshot`。它必须包含稳定的 `executionId`，并且由宿主确保属于当前聊天和当前父消息。

## 5. 插件展示接口草案

### 5.1 胶囊展示插件

建议新增独立的 Agent UI 注册函数，不修改已发布的 `registerChatViewSlotPlugin`：

```ts
ToolPkg.registerChatAgentUiPlugin({
  id: "opencode_agent_ui",
  agentIds: ["toolpkg:opencode_agent:*"],
  function(event) {
    return {
      modeCapsule: {
        label: event.mode.mode === "plan" ? "Plan" : "Build",
        subtitle: `${event.mode.completedTodoCount}/${event.mode.todoCount}`,
        icon: event.mode.mode === "plan" ? "ClipboardList" : "Wrench",
        actions: event.mode.canRequestModeChange
          ? ["open_todo", "open_plan", "request_mode_change"]
          : ["open_todo", "open_plan"]
      },
      todoCapsule: {
        currentTodoId: event.todo.currentTodoId,
        activeSubagentCount: event.todo.activeSubagentCount
      }
    };
  }
});
```

以上名称和字段仍需在阶段 0 冻结。插件返回值只能是数据对象，不能包含：

- `composeDsl`
- `onClick` 函数
- `sendMessage`
- `callTool`
- 任意 IPC channel
- 任意可执行脚本

### 5.2 子代理卡片插件

已有设计中的 `ToolPkg.registerChatSubagentViewPlugin` 保持独立，不与胶囊接口合并：

```ts
ToolPkg.registerChatSubagentViewPlugin({
  id: "opencode_subagent_cards",
  agentIds: ["toolpkg:opencode_agent:*"],
  function(event) {
    return {
      title: event.execution.agentDisplayName,
      subtitle: event.execution.status,
      summary: event.execution.summary,
      icon: "Bot"
    };
  }
});
```

`agentIds` 是宿主过滤条件。插件不能通过 `supports()` 观察或装饰其他插件的 execution。

### 5.3 纯展示执行环境

当前 ToolPkg 的 hook 函数运行在完整 JavaScript 环境中，仅限制返回值并不能保证没有副作用。新的 Agent UI API 必须选择以下一种实现：

1. 注册时只保存静态展示定义，不保存可执行 render 函数
2. 为 UI render 函数创建无 `Tools`、无 `NativeInterface`、无 IPC 的纯展示上下文
3. 将所有展示定义编译成宿主可验证的 JSON，并在宿主侧渲染

在纯展示边界落地前，不应把该接口标记为只读安全接口。

## 6. 宿主动作协议

胶囊可以声明动作，但动作由宿主接收和校验：

```ts
export type AgentUiActionType =
  | "open_todo"
  | "open_plan"
  | "open_subagent_detail"
  | "request_mode_change";

export interface AgentUiAction extends ToolPkg.JsonObject {
  type: AgentUiActionType;
  sessionId: string;
  executionId?: string;
  targetMode?: AgentMode;
}
```

动作处理规则：

- `open_todo` 只打开宿主 Todo 详情
- `open_plan` 只打开宿主 Plan 详情
- `open_subagent_detail` 只打开指定 execution 的只读页面
- `request_mode_change` 只提交切换请求，不直接改变 Agent 状态
- 宿主根据 session 状态、权限和当前回合决定是否接受请求
- 插件不能伪造 `completed`、`running` 或 `mode` 状态

本阶段实现可以只记录 action，不连接 Agent runtime。这样新增文档和未来类型不会影响当前聊天发送流程。

## 7. 状态来源与所有权

状态来源固定为宿主：

```text
AgentKernel
  -> AgentSessionStore
  -> AgentUiSnapshot
  -> ChatAgentUiBridge
  -> Host Capsule / Host Readonly Detail
```

插件拥有：

- Agent profile 的展示名称
- Todo 文案和本地化
- Plan / Build 胶囊的图标与颜色建议
- 子代理卡片的标题、摘要和图标

插件不拥有：

- execution 的创建、完成、失败和取消
- tool call 的状态
- Todo 的真实状态
- mode 的真实切换结果
- permission request
- 父消息锚点
- Room 数据或 DAO

## 8. 与当前实现的兼容性

本设计不修改：

- `ToolPkg.registerChatViewSlotPlugin`
- `ChatViewSlotPluginRegistry`
- Classic / Agent 输入区现有三个插槽
- 已发布 Prompt、Summary、Chat Input、Chat Message 和 Tool Lifecycle Hook
- `Tools.Chat` 的参数和行为
- 现有聊天消息持久化格式
- 当前 native 角色卡和群聊流程

新接口必须具备独立 capability，例如 `agent_ui_v1`。不具备该 capability 的宿主不应加载新注册项，也不应改变旧插件行为。

Agent UI 插件启用和 Agent runtime 激活是两件事：

- 启用插件：注册展示能力
- 激活 Agent：某个聊天绑定 Agent session
- 关闭插件：停止新 UI 事件订阅，不删除历史执行记录

## 9. 验收标准

### 9.1 胶囊

- Plan / Build 胶囊能显示当前模式和 Todo 进度
- 胶囊显示来自宿主 snapshot
- 胶囊点击只产生宿主 action
- 未接入 Agent runtime 时点击不会改变当前聊天行为
- 主界面和浮窗不会互相覆盖状态

### 9.2 Todo / Plan

- Todo 顺序由 snapshot 的 `order` 决定
- 重组、旋转和重新打开不会重复创建 Todo
- Todo 状态更新可以回放
- Plan 详情不带输入框和执行按钮
- Plan 文件不存在或加载失败时，UI 显示明确的不可用状态

### 9.3 子代理

- 卡片只出现在所属父消息附近
- 卡片只匹配声明的 `agentIds`
- `executionId` 可以打开固定只读详情
- 工具调用数量和状态可以实时更新并在重启后回放
- 卡片插件不能修改 execution 状态

### 9.4 兼容

- 旧 ToolPkg 不需要重新打包
- 旧 ChatView Slot 行为不改变
- 旧角色卡聊天不显示虚假的 Agent 状态
- 新接口未实现时，当前聊天仍按原链路运行

## 10. 非目标

- 本文件不实现 Agent loop
- 本文件不实现 Agent 权限评估
- 本文件不实现 Plan/Build 状态机
- 本文件不实现子代理创建和取消
- 本文件不实现消息历史 projection
- 本文件不允许插件通过 UI API 执行工具
- 本文件不替代 [`07_agent_architecture_conclusions.md`](07_agent_architecture_conclusions.md)

## 11. 后续步骤

1. 冻结 `AgentUiSnapshot`、`AgentUiAction` 和 `agentIds` 过滤规则
2. 冻结纯展示执行环境
3. 冻结宿主固定胶囊和详情布局
4. 在 AgentKernel 文档确认 session、run、tool call 的数据关系
5. 再添加 ToolPkg 类型声明和注册捕获
