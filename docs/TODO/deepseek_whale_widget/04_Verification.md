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
- ToolPkg `v0.2.0` SHA-256：`ff23693a952be6e3b80a6b551cbcda0128edc0df346f9162adfef150dbd3e3fd`
- 远程 `assembleRelease` 构建通过，目标提交 `75004e544`
- Release APK SHA-256：`28ef4c47748280ceafce99de9b7ed264cf7ff8fab9a2cd857fa6a0d7b24c79f2`
- `toolpkg.floating_window.v2`、双浮窗恢复顺序和拖动跟随位置已通过编译验证
