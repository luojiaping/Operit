---
title: ToolPkg 聊天输入区 UI 插槽
fork: https://github.com/luojiaping/Operit
status: in_progress
---

# ToolPkg 聊天输入区 UI 插槽

## 当前状况

Operit 已有聊天输入 Hook，可以监听输入和提交行为，但 ToolPkg 不能在聊天输入
区域内部渲染自己的 UI。需要显示在输入框上方的插件只能使用独立悬浮窗，无法
跟随聊天输入区布局。

上游曾短暂实现过 Chat View Slot，但该接口后来整体回滚。本次不恢复旧的
`registerToolPkgChatViewSlotPlugin` 名称，改用新的 `registerInputSlotPlugin`，
避免与当前事件型 `registerChatViewHook` 混淆。

## 目标

- 为 ToolPkg 提供聊天输入区的三个宿主 UI 插槽
- 支持普通文本和 `compose_dsl` screen
- 复用现有 ToolPkg main hook、超时预算和 Compose DSL renderer
- 不把输入框每次击键都变成一次宿主 Hook 执行
- 插件停用或重新加载后及时更新插槽内容

## 插槽

- `above_input`：输入区容器顶部，位于回复预览、队列和输入控件之前
- `input_drawer`：输入控件所在容器内部、输入控件之前
- `input_toolbar_right`：模型选择器右侧的输入工具栏区域

## 步骤

1. [IN_PROGRESS: 接口、注册管线和 renderer](./01_InterfaceAndRendering.md)
2. 待完成：Classic/Agent 输入区接入和示例包验证
3. 待完成：文档、静态检查和设备验证

## 约束

- 插槽是宿主聊天页面内嵌 UI，不申请系统悬浮窗权限
- capability 不属于 `required_host_capabilities`；插槽使用 ToolPkg 注册 API
- `composeDsl.screen` 必须是当前 ToolPkg 归档内可解析的 Compose DSL screen
- Hook 失败、超时和空返回不应破坏原有输入区
