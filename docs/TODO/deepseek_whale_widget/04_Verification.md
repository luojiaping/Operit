---
title: 验证与交付
status: in_progress
---

# 验证与交付

## 计划

- 静态核对 Kotlin `@JavascriptInterface`、ToolPkg TypeScript 类型和注册脚本
- 核对 capability、包启停、凭据脱敏和 DTO schema
- 核对余额 JSON、平台用量 JSON、初次基线、日期边界和浮窗 disabled state
- 本轮先完成宿主通用接口和独立插件源码改造，遵守仓库约束暂不触发构建、编译或测试命令

## 完成记录

- 独立插件 `manifest.json` 结构和 Whale PNG 资源已核对
- `git diff --check` 通过
- 独立插件 `src` 与 `dist` 逐文件同步
- 插件 `manifest.json` 声明 `toolpkg.floating_window.v3` 和 `0.3.0`
- v3 字段已覆盖注册 parser、runtime model、PackageManager DTO、host bridge、service、JS facade 和 TypeScript 类型
- `Text`、`BasicText`、Canvas `text`/`drawText` 已接入 `textAlign`
- 待执行宿主编译、插件重新打包、远程 APK 构建和设备上的动画、音频、跟随与对齐验证
