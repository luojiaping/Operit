---
title: 主题包可调设置
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# 主题包可调设置

## 背景

当前 V2 参数只完成声明、持久化和类型校验。默认主题没有任何参数，主题设置页仅对历史 `primary_color` 与 `background_image` ID 做硬编码分支，运行时也不会把参数投影到 token、Material 色板或 scene。因此没有导入外部包时，用户几乎无法调整视觉。

本批把参数升级为主题作者声明的控件和受限视觉 effect。普通用户只看到当前主题明确公开的选项，不编辑 M3 role、frame、token、scene DSL 或包资源。

## 产品边界

- 设置页由 Theme、Appearance、Conversation 三个紧凑平面分组组成。
- Theme 负责活动包选择、导入、卸载和刷新。
- Appearance 提供深浅模式、字号，以及活动主题公开的颜色和背景选项。
- Conversation 提供聊天样式和输入样式。
- 模型信息、时间戳、统计、通知、工具和系统行为继续留在 Display & Behavior。
- 默认主题公开 accent color 与本地 stage background image。
- effect 只在声明它的活动包上运行。默认主题的可调效果不自动改写 Cyber Grid；后者需要时自行声明选项。

## 未发布清理

主题设置 UI 与 `2.1.0` preview 都不构成发布接口。本批升级 package document schema 至 `3`，删除 `ThemePackageParameterIdsV2`、固定色板列表、`STRING` 背景图片特例和旧主题设置区。不会保留 schema 2 的解析路径。

## 目录

1. [参数契约与运行时](1_ParameterContractAndRuntime.md)
2. [紧凑主题设置页](2_CompactThemeSettings.md)
3. [主题包、测试与验收](3_ThemeArtifactsTestingAndAcceptance.md)

## 进展

[DONE] schema 3 parameter control/effect、default accent/background、紧凑 Theme & Appearance 页面与 archive lock 已实现。package script、release metadata 和静态检查已更新。

[TODO] 执行 focused tests、release build、设备验收，并在用户明确要求后发布新的 GitHub preview。
