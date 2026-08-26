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
- 插件 `manifest.json` 已声明 `toolpkg.floating_window.v4` 和 `0.4.0-test.2`
- v4 字段已覆盖注册 parser、runtime model、PackageManager DTO、host bridge、service、JS facade 和 TypeScript 类型
- `Text`、`BasicText`、Canvas `text`/`drawText` 已接入 `textAlign`
- 远程 `sync + build_release` 通过，宿主提交 `88e850cfa`，耗时 `368.05s`
- Release APK：`operit-release-fix_api-interface-88e850cf.apk`
- Release APK SHA-256：`b53aaed3bd49fbc74cacbe5d12b9f30f459fc1a80e9b348f414f1fb3969ec3ec`
- 独立插件 `sh scripts/pack.sh` 与 ZIP 完整性校验通过
- 测试 ToolPkg `0.4.0-test.2` 已重新打包并通过 ZIP 校验
- 测试 ToolPkg SHA-256：`102245e64a83c6f29584f0e436473068e9e5f7eb9542c0e08c51450adfa16bb1`
- 测试 ToolPkg [`v0.4.0-test.2` prerelease](https://github.com/luojiaping/operit-deepseek-whale-widget/releases/tag/v0.4.0-test.2) 已上传
- 待执行设备上的按下/松开音频、拖动跟随和 `0.6x - 2.5x` 缩放截图验证
