---
title: ToolPkg 与悬浮窗
status: complete
---

# ToolPkg 与悬浮窗

## 计划

- 创建 `dsh-whale-widget` ToolPkg 示例包
- 注册设置页、余额页、长期驻留悬浮窗和资源图片
- 使用宿主桥接读取缓存快照，使用插件开关控制 active state
- 保存显式显示状态、位置和尺寸
- 插件停用时释放浮窗 Compose 和 JavaScript runtime

## 完成记录

`examples/deepseek_whale_widget` 使用源码路径作为 ToolPkg 入口，包含设置页、余额页、长期驻留悬浮窗、平台 Token 设置和 Whale PNG 资源。
