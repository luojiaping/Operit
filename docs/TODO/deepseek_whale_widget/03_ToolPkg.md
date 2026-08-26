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
- 将鲸鱼和气泡拆成两个浮窗，气泡通过通用 `follow` 声明跟随鲸鱼
- 让两个窗口共享尺寸比例和 `routeArgs.scale`

## 完成记录

独立插件仓库使用 `src` 作为编辑源、`dist` 作为 ToolPkg 入口，包含设置页、余额页、独立鲸鱼窗口、跟随气泡窗口、平台 Token 设置和 Whale PNG 资源。

通用浮窗协议由宿主提供，鲸鱼窗口只声明 `follow`、`pressFeedback` 和 `releaseFeedback`，不在宿主实现中保留插件专用逻辑。
