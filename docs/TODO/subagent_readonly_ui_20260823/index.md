---
fork: https://github.com/luojiaping/Operit.git
scope: 子代理消息卡片、持久化执行记录和宿主固定只读详情接口设计
operit_commit: 30d6c0124
opencode_commit: 9d466cd8497d02db40010077201e07bd10ac33b4
---

# 子代理只读 UI 接口

本文档是跨窗口协作入口。另一个开发窗口应先阅读本文件，再按编号阅读后续设计文档。

## 结论

当前已实现的 `Chat View Slot` 是聊天输入区扩展接口，不能直接承载聊天消息中的子代理卡片。子代理需要一套独立的“消息区域视图接口”：

- 子代理执行由宿主 Agent runtime 创建并持久化。
- ToolPkg 只注册卡片展示规则，不创建或修改执行记录。
- 卡片由宿主插入父消息附近，点击后由宿主打开固定的只读详情页。
- 详情页显示子代理提示词、状态、摘要和内部工具调用，但没有输入框、发送、编辑、重试或执行按钮。
- 详情页不使用任意 Compose DSL，避免 UI 层通过 `callTool`、`sendMessage` 或通用 IPC 改变执行状态。

本次只生成开发文档，子代理接口尚未实现。

## 已实现接口

上一轮已在 `development` 提交 `30d6c0124` 中加入 Chat View Slot：

- 宿主 Registry、插槽常量和渲染结果：[`ChatViewSlotPluginRegistry.kt`](../../../app/src/main/java/com/ai/assistance/operit/plugins/chatview/ChatViewSlotPluginRegistry.kt)
- ToolPkg Bridge：[`ToolPkgChatViewSlotBridge.kt`](../../../app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgChatViewSlotBridge.kt)
- ToolPkg 注册捕获和解析：[`JsToolPkgRegistration.kt`](../../../app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsToolPkgRegistration.kt)、[`ToolPkgParser.kt`](../../../app/src/main/java/com/ai/assistance/operit/core/tools/packTool/ToolPkgParser.kt)
- UI 插槽接入：[`ClassicChatInputSection.kt`](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/classic/ClassicChatInputSection.kt)、[`AgentChatInputSection.kt`](../../../app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/style/input/agent/AgentChatInputSection.kt)
- ToolPkg 类型声明：[`toolpkg.d.ts`](../../../examples/types/toolpkg.d.ts)、[`core.d.ts`](../../../examples/types/core.d.ts)
- API 文档：[`toolpkg.md`](../../doc-src/package-dev/toolpkg.md)

上一轮没有移植 Agent 注册、Agent 权限、Agent Prompt 或 `EnhancedAIService` Agent 上下文。

上一轮提交已通过远程 Release 构建：

- artifact: `operit-release-development-30d6c012.apk`
- size: `403080863` bytes
- SHA-256: `764c7efa29c955b1ae59532703e6e1893cd161023c2ebcd5e7d95ffa8744cc5e`
- build duration: `511.42s`

## 本文档目录

1. [`01_existing_contracts.md`](01_existing_contracts.md)：上一轮接口完整契约和当前边界
2. [`02_persistence_and_execution_model.md`](02_persistence_and_execution_model.md)：执行记录、工具调用记录和消息锚点
3. [`03_plugin_api_and_readonly_ui.md`](03_plugin_api_and_readonly_ui.md)：新插件接口、卡片和固定只读详情页
4. [`04_opencode_mapping.md`](04_opencode_mapping.md)：OpenCode child session/tool part 到 Operit 的映射
5. [`05_implementation_plan.md`](05_implementation_plan.md)：分阶段实施、验收和风险控制
6. [`06_opencode_ui_contract.md`](06_opencode_ui_contract.md)：Todo、Plan/Build 胶囊和子代理 UI contract
7. [`07_agent_architecture_conclusions.md`](07_agent_architecture_conclusions.md)：本窗口完整调研证据、架构结论和后续边界
8. [`08_agent_boundary_foundation.md`](08_agent_boundary_foundation.md)：第一阶段 Agent contract、执行记录和历史 owner projection 实现记录
9. [`09_agent_router_history_boundary.md`](09_agent_router_history_boundary.md)：显式 root session 路由、稳定消息身份和 owner-aware history reader

上一轮接口的移植记录仍保留在 [`chat_view_slot_plugin`](../chat_view_slot_plugin/index.md)。

## 已确认取舍

- 详情页：宿主固定只读 UI
- 记录保留：Room 持久化，应用重启后仍可查看
- 数据所有权：默认由宿主 Agent runtime 产生，ToolPkg 只负责展示

## 尚未实现的边界

Agent 的 Plan/Build 身份、会话切换、权限物化、工具执行授权和专属 Prompt 仍需独立实现。本文档只规定它们将来向子代理 UI 提供什么稳定执行记录，不替代 Agent runtime 设计。

完整的 Agent runtime、角色卡隔离、权限、typed tool call 和 OpenCode 插件化结论见 [`07_agent_architecture_conclusions.md`](07_agent_architecture_conclusions.md)。

[DONE: design documentation]
