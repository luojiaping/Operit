---
title: 验证与交付
status: in_progress
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
- 上一轮 `v0.1.4` 构建记录保留在历史提交；本轮 `v0.2.0` 尚未重新打包
- 待验证宿主协议 `toolpkg.floating_window.v2`、双浮窗恢复顺序和拖动跟随位置
- 待验证 `0.6x - 2.5x` 下鲸鱼窗口、气泡窗口和内部文字的比例一致
