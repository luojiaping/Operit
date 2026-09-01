# 紧凑主题设置页

## 旧实现

`ThemePackagesScreen` 常驻显示完整安装列表，并硬编码 primary color 色块和 background image `STRING` 特例。`GlobalDisplaySettingsScreen` 又重复显示主题模式、字号、聊天样式和输入样式，导致用户无法判断哪些设置真正影响活动主题。

## 新结构

1. Theme：活动主题行打开单选面板；导入、刷新与卸载保留为明确命令。
2. Appearance：系统/浅色/深色分段控制、字号 slider，以及 manifest 驱动的主题参数。
3. Conversation：Cursor/Bubble 与 Agent/Classic 分段控制。

设置行即时写入全局 presentation 或活动 `ThemeInstanceV2`。参数 UI 只渲染当前活动包声明且具有活动 effect 的控件。使用 section/list skin、稳定行高、色块和图标命令，不使用嵌套卡片或长说明文本。

## 最小功能单元

[DONE] 1. 提取可测试的主题设置 content 和参数控件 renderer。

[DONE] 2. 把 mode/font/chat/input 从 Display & Behavior 移入主题页，并清理旧 UI。

[DONE] 3. 接入主题选择面板、颜色 picker、背景选择/清除和即时参数写入。不可链接 archive 显示为 disabled，不允许写入 active selection；同坐标选择不清空参数；窄屏或大字号改用单选行，色板自动换行。

## 验收

- 默认主题未导入任何外部 archive 时仍显示可调 accent 与背景选项。
- 外部主题只显示其自身公开的可见 option；无 option 的主题不出现空分组。
- 主题选择、参数 reset、模式、字号、聊天样式和输入样式都保持原有持久化语义。
- 窄屏和大字号下分段控件与色块不溢出。
