---
title: ToolPkg 与悬浮窗
status: in_progress
---

# ToolPkg 与悬浮窗

## 计划

- 创建 `dsh-whale-widget` ToolPkg 示例包
- 注册设置页、余额页、长期驻留悬浮窗和资源图片
- 使用宿主桥接读取缓存快照，使用插件开关控制 active state
- 保存显式显示状态、位置和尺寸
- 插件停用时释放浮窗 Compose 和 JavaScript runtime
- 将鲸鱼和气泡拆成两个浮窗，气泡通过 `followWindowId` 跟随鲸鱼
- 让两个窗口共享尺寸比例和 `routeArgs.scale`

## 完成记录

`examples/deepseek_whale_widget` 使用源码路径作为 ToolPkg 入口，包含设置页、余额页、独立鲸鱼窗口、跟随气泡窗口、平台 Token 设置和 Whale PNG 资源。
