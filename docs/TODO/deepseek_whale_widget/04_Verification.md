---
title: 验证与交付
status: in_progress
---

# 验证与交付

## 计划

- 静态核对 Kotlin `@JavascriptInterface`、ToolPkg TypeScript 类型和注册脚本
- 核对 capability、包启停、凭据脱敏和 DTO schema
- 核对余额 JSON、平台用量 JSON、初次基线、日期边界和浮窗 disabled state
- 本轮完成宿主通用接口和独立插件源码改造；用户明确要求后通过远程 Builder 执行 Release 编译

## 完成记录

- 独立插件 `manifest.json` 结构和 Whale PNG 资源已核对
- `git diff --check` 通过
- 独立插件 `src` 与 `dist` 逐文件同步
- `v0.3.0` 误发布 Release 与 tag 已删除，测试期不保留该版本
- 待核对插件 `manifest.json` 的 `toolpkg.floating_window.v4` 和 `0.4.0-test.1`
- 待核对 v4 字段覆盖注册 parser、runtime model、PackageManager DTO、host bridge、service、JS facade 和 TypeScript 类型
- `Text`、`BasicText`、Canvas `text`/`drawText` 已接入 `textAlign`
- 待执行新的远程 Release 编译、测试 ToolPkg 打包和设备上的动画、音频、跟随与对齐验证
