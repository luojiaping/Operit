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
- 远程 `assembleRelease` 构建通过，目标提交 `4da5364b0`
- Release APK SHA-256：`7d3796331f7de2d14cc81fa73f3b41fb9d9606a8103eca56044764c910065cb4`
- ToolPkg `v0.1.4` SHA-256：`bd76ed16677a7830238b5129b85bcc682e5f02d87a88ae44ea9f0faf2044b57b`
- `v0.1.4` manifest、音效、GIF、Slider 和 `src/dist` 同步校验通过
