---
title: 验证与交付
status: complete
---

# 验证与交付

## 计划

- 静态核对 Kotlin `@JavascriptInterface`、ToolPkg TypeScript 类型和注册脚本
- 核对 capability、包启停、凭据脱敏和 DTO schema
- 核对余额 JSON、平台用量 JSON、初次基线、日期边界和浮窗 disabled state
- 本次执行通过远程 Builder 编译 Release，并在独立插件仓库重新打包

## 完成记录

- `jq empty examples/deepseek_whale_widget/manifest.json` 通过
- `file examples/deepseek_whale_widget/resources/whale.png` 确认为 610x610 RGBA PNG
- `git diff --check` 通过
- 独立仓库 `sh scripts/build.sh`、`sh scripts/pack.sh` 和 `.toolpkg` ZIP 校验通过
- 远程 `assembleRelease` 构建通过，目标提交 `29f07e491`
- Release APK SHA-256：`2e7299d5e999dbe243eb0f000b47ea53c389d4075eb707cce228e37673eed400`
- ToolPkg `v0.1.3` SHA-256：`039d37ac0d8d9601b8c938afdd12c50125b1fd6969e9b75d552b17aa370aaea4`
