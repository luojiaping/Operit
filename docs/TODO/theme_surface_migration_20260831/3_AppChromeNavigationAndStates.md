# 应用壳、导航与状态层

## 目标 surface

- `app.shell`
- `app.navigation`
- `overlay.snackbar`
- `overlay.toast`
- `state.loading`
- `state.empty`
- `state.error`

## 旧实现

`AppShellSceneHost` 已主题化主壳背景和顶栏布局，但手机抽屉、平板导航、route content 的 page 根、全局加载和错误层仍直接使用 Material 视觉。页面进入不同路由时，聊天之外的内容区域缺少赛博主题明确的底板、分区和状态框。

## 介入点

- `ui/main/components/AppContent.kt`
- `ui/main/components/DrawerContent.kt`
- `ui/main/layout/PhoneLayout.kt`
- tablet navigation/rail 组件
- `CustomScaffold` 的共享 snackbar 与反馈入口

## 实施步骤

1. 将 route content 包进 `ThemeSurfaceHostV2(TEMPLATE)` 的 `page` primitive。
2. 将加载、空态、错误层改为对应 `state.*` surface 与 `status` skin。
3. 将抽屉和 tablet rail 的 item、selected state、分区标签接入 `navigation`/`section` skin。
4. 将共享 snackbar/toast 容器接入 `overlay.*`，避免各页自行固定颜色。
5. 检查状态栏、导航栏、drawer scrim、IME padding 和 edge-to-edge 不被页面 frame 覆盖。

## 验收

- 手机和 tablet 所有 route 都有 package-owned page 底板。
- 导航项 normal/selected/disabled 状态均随 active package 改变。
- loading/empty/error 不再显示普通 Material card。
- back、drawer 手势、转场、screen cache 和 ViewModelStore 行为不变。

## 进展

[DONE] `AppContent` 的 route content 已由 page template 接入；全局 loading 已接入 `state.loading` surface 与 `status` skin。

[PARTIAL] Phone drawer 与 collapsed tablet drawer 已接入 `app.navigation` surface；文本和 icon 导航项均消费 `navigation` skin。drawer 信息卡和 shortcut card 仍待改用 V2 primitive。

[PARTIAL] `ChatToastHost` 已通过 `overlay.toast` 使用 `snackbar` skin，Package Manager snackbar 也已通过 `overlay.snackbar` 试点接入；其余 toast/snackbar、empty/error 与 drawer shortcut/info card 待迁移。
