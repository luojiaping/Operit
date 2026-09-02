---
title: 全 UI 主题 Surface 分批迁移
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# 全 UI 主题 Surface 分批迁移

> [SUPERSEDED] schema 3 package document 已由 [schema 4 参数契约](../theme_parameter_contract_rebuild_20260901/) 取代；本目录的表面迁移记录仍保留，但其中 schema 3 artifact 描述不再是当前基线。

## 背景

V2 主题包已经声明 37 个日常 surface 和 16 个组件 skin，但运行时只实际渲染 `app.shell` 与 `chat.main`。其余 `TEMPLATE`、`HOST_SHELL` 和通用 component skin 大多没有真实消费端，因此赛博主题在聊天页外主要只表现为 Material 色板变化。

聊天页已经建立了深色底板、青色轨道、洋红强调、HUD 缺口、开放角括号和切角卡片的视觉语言。本计划将该语言扩展到全部 Operit 自有日常 UI，同时保持每个页面的业务、导航、滚动、IME、焦点与无障碍语义仍由宿主代码控制。

## 目标

- 让每个 Operit 原生 root 绑定一个稳定 `ThemeSurfaceIdV2`。
- 让 `SCENE`、`TEMPLATE`、`HOST_SHELL` 都有真实且可验证的 V2 renderer。
- 将通用页面、导航、分区、列表、动作、输入、弹层和状态组件逐批接入 V2 skin。
- 为赛博主题建立覆盖全应用的统一视觉系统，而非把聊天页的外观复制到每个页面。
[SUPERSEDED] 保持当前 V2 `2.1.0` 临时主题包可导入；本计划第一阶段不改变 manifest schema。

当前主题包 document 使用 schema 3；`2.1.0` 开发 preview 不构成兼容边界。

## 所有权边界

主题包控制 Operit 自有页面、应用壳、导航、弹层、状态层、浮动聊天、应用内权限壳、WebChat 和外部内容的 Operit 外框。

主题包不重绘插件自有 Compose DSL、插件 WebView DOM、插件 Canvas、Android 系统权限框、SAF、输入法、首次启动、崩溃报告、数据修复页和桌面小组件。浏览器、终端、媒体与插件页面只迁移 Operit 所有的壳层。

## 当前事实

- `app.shell`：已由 `AppShellSceneHost` 实际渲染。
- `chat.main`：已由 `ChatMainSceneHost` 实际渲染。
- 聊天 header、消息、composer 与 input：已消费 `ThemeComponentSurfaceV2`。
- 其余原生 route 已由 `ThemeSurfaceHostV2` 消费对应 page 或 host-shell skin；导航、loading、WebSession dialog/sheet、chat toast、Package Manager snackbar 与 Repo Market category menu 也已有 production consumer。
- `chat.floating`、`chat.permission_overlay`、`browser.shell`、`web_chat.main`、`media.shell`、`state.empty` 与 `state.error` 仍只有 manifest/policy 覆盖，待后续 batch 接入。

## 迁移原则

1. 每批只迁移一个清晰的 UI 家族，并记录对应 surface、入口、旧实现和验收。
2. 优先接入共享 primitive，再迁移页面；不为每个页面复制主题代码。
3. `TEMPLATE` 是正式页面 renderer，`HOST_SHELL` 是正式外壳 renderer；二者都不允许仅作为清单标签。
4. 主题只决定视觉，宿主继续控制路由、数据、行为、滚动、IME、焦点、语义和平台调用。
5. 不在 Compose 渲染路径隐藏无效 surface 或无效主题；链接与路由覆盖仍保持严格。

## 分批目录

1. [Surface 覆盖矩阵](0_CoverageMatrix.md)
2. [Surface 绑定与运行时](1_SurfaceBindingAndRuntime.md)
3. [通用组件 Primitive](2_ComponentPrimitives.md)
4. [应用壳、导航与状态层](3_AppChromeNavigationAndStates.md)
5. [设置、助手与包管理](4_SettingsAssistantAndPackages.md)
6. [市场与编辑器](5_MarketAndEditors.md)
7. [工作区与工具箱](6_WorkspaceAndToolbox.md)
8. [独立宿主与弹层](7_DetachedHostsAndOverlays.md)
9. [主题包与归档](8_ThemePackagesAndArtifacts.md)
10. [测试、发布与验收](9_TestingRolloutAndAcceptance.md)
11. [Overlay Primitive 试点](10_OverlayPrimitivesPilot.md)
12. [Input Primitive 试点](11_InputPrimitivePilot.md)
13. [Operation Status 与共享 Overlay 试点](12_OperationStatusAndSharedOverlayPilot.md)

## 依赖

- [全应用主题所有权](../global_theme_ownership_20260831/index.md)
- [异形组件边框](../cyber_component_frames_20260831/index.md)
- [聊天主题可见性修复](../chat_theme_visibility_repair_20260831/index.md)

## 进展

[DONE] 第一批基础设施：surface 绑定、通用页面宿主与 loading 状态层。

[PARTIAL] 第二批：共享 navigation、section、choice、standard action primitive 已接入；其余输入、弹层和反馈组件待迁移。

[DONE] 第二批 overlay primitive 试点：WebSession dialog/sheet 与 chat toast 已接入现有 V2 skin。

[DONE] 第二批 input primitive 试点：WebSession、Memory 与 File Manager 的无 label text field 已接入 input skin。

[DONE] 上一批全局 surface coverage 已推送并完成 release 编译，产物记录见 [测试、发布与验收](9_TestingRolloutAndAcceptance.md)。

[DONE] Overlay primitive 试点已完成 release 编译；产物记录见 [测试、发布与验收](9_TestingRolloutAndAcceptance.md)。JVM/Android test 尚未执行。

[DONE] Input primitive 试点已完成 release 编译；产物记录见 [测试、发布与验收](9_TestingRolloutAndAcceptance.md)。JVM/Android test 尚未执行。

[DONE] 第三批共享反馈与 overlay 最小生产试点：Package Manager snackbar、Repo Market category menu 与 operation status success/error 已接入 V2 skin；focused tests 已新增但尚未执行。

[TODO] 后续页面家族、独立宿主、主题归档与设备验收按各步骤文档推进。
