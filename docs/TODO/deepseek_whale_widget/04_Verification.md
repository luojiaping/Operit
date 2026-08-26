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
- 插件 `manifest.json` 声明 `toolpkg.floating_window.v3` 和 `0.3.0`
- v3 字段已覆盖注册 parser、runtime model、PackageManager DTO、host bridge、service、JS facade 和 TypeScript 类型
- `Text`、`BasicText`、Canvas `text`/`drawText` 已接入 `textAlign`
- 远程 `sync + build_release` 通过，宿主提交 `a9fe57d88`，耗时 `415.02s`
- Release APK：`operit-release-fix_api-interface-a9fe57d8.apk`
- Release APK SHA-256：`e47e10ebeb70ba19bcccff1c4f72ed56dcff911bea0a00910abe548681820875`
- 独立插件 `sh scripts/pack.sh` 与 ZIP 完整性校验通过
- ToolPkg `v0.3.0` SHA-256：`22c8e89732936dd1a19d9eb304395809e95ec5d0478f8220b955aa620334259c`
- ToolPkg [`v0.3.0` Release](https://github.com/luojiaping/operit-deepseek-whale-widget/releases/tag/v0.3.0) 已发布
- 待执行设备上的动画、音频、跟随与对齐验证
