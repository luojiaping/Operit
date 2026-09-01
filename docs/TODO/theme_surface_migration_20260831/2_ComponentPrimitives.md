# 通用组件 Primitive

## 旧实现

聊天已经使用 `ThemeComponentSurfaceV2`，但导航、页面、分区、列表、按钮、弹层和状态组件仍大量直接使用 Material 容器或 `NativeTheme*V1` renderer。主题包中的 `navigation`、`page`、`list_item`、`button`、`dialog`、`sheet`、`menu`、`snackbar`、`status` skin 因此没有全局视觉影响力。

## 目标实现

建立只面向 Operit 自有 Compose UI 的 V2 primitive。每个 primitive 由 skin 提供 container、content、frame、elevation 与 content padding；调用方继续传入事件、语义、图标、文本和业务状态。

| Primitive | Component skin | 首批调用方 |
| --- | --- | --- |
| 页面根 | `page` | AppContent route template |
| 导航项 | `navigation` | Drawer、tablet rail、market tab |
| 分区 | `section` | 设置、包管理、市场表单 |
| 列表项 | `list_item` | 通用设置行、文件、市场条目 |
| 动作 | `button`、`icon_button` | 设置、工具、市场操作 |
| 输入容器 | `input` | 表单、搜索、筛选 |
| dialog/sheet/menu | `dialog`、`sheet`、`menu` | 通用确认、选择和编辑弹层 |
| 反馈 | `snackbar`、`status` | 任务进度、结果、错误消息 |

## 迁移方式

1. 先新增 V2 wrappers，避免在每个页面直接手写 `ThemeComponentSurfaceV2`。
2. 将通用 `NativeTheme*V1` renderer 的视觉实现改为调用 V2 wrappers，保持既有 public Compose contract。
3. 对 Material 输入、按钮和选择控件只替换 visual container；焦点、键盘、点击和无障碍仍交给 Material/Compose。
4. 固定高度控件使用稳定尺寸，避免 frame 绘制导致布局抖动。

## 验收

- 每个 skin 有正常、disabled、selected、focused 或 error 的实际消费者。
- 原主题切换不改变控件的点击目标、语义 role、IME 行为和焦点顺序。
- 赛博主题的 page、section、list、button、input、dialog、sheet、menu、snackbar、status 均能在生产页面截屏中识别。

## 进展

[DONE] `ThemeSurfaceHostV2` 已将 Operit route template 接入 `page` skin；`HOST_SHELL` 以 `section` skin 作为外部内容的 Operit 外框。

[DONE] page 与 host shell 使用非裁切 frame 绘制，canvas、terminal、plugin 与 AndroidView 内容不会被 package shape 裁切；普通组件仍保留裁切行为。

[DONE] 主抽屉文本导航项已改由 `navigation` normal/selected/disabled skin 绘制，既有 selectable/clickable 语义保持不变。

[DONE] 既有 `NativeThemeSectionV1` 已改由 `section` skin 绘制，备份与设置共享分区开始获得 package frame。

[DONE] `list_item` 已用于 `NativeThemeChoiceItemV1`，覆盖备份/导入流程中的 radio choice normal/selected/disabled 状态。

[DONE] `NativeThemeActionButtonV1` 的 standard emphasis 已改由 `button` normal/disabled skin 绘制，保留 button role 与点击语义。caution/destructive 继续使用明确的 tertiary/error Material 容器，等待主题包提供语义化状态 skin。

[DONE] `status` 已用于 AppContent 全局 loading；`NativeThemeOperationStatusV1` 的 success/error 现分别消费 `status.normal`/`status.error`。direct `status` skin 缺少 error state 会被 archive validator、linker 与两个主题 package script 拒绝。

[DONE] `section` 与 `list_item` 的 supporting text 已从 package `contentToken` 派生，避免次级文本绕过 skin 内容色。

[DONE] `ThemeOverlaySurfaceHostV2` 已将 `overlay.dialog`、`overlay.sheet`、`overlay.menu`、`overlay.snackbar`、`overlay.toast` 分别连接到匹配 skin；WebSession 手写 dialog/sheet、ChatToastHost、Package Manager snackbar 与 Repo Market category menu 已成为生产消费者，窗口、dismiss、IME、动画和队列仍由原调用方控制。

[DONE] `ThemeOutlinedTextFieldV2` 已将无 floating label 的 `String`-backed Material field 接入 input skin，WebSession prompt、Memory search 与 File Manager search 已成为生产消费者。focused/error input skin 现为 archive/linker 强制交互状态，默认和 Cyber Grid `2.1.0` 已满足。

[TODO] 其余 list item 家族、input、dialog、sheet、menu、snackbar consumer 仍待后续 batch。
