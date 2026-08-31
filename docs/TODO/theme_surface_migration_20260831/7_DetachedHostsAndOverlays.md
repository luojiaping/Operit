# 独立宿主与弹层

## 目标 surface

- `chat.floating`
- `chat.permission_overlay`
- `browser.shell`
- `web_chat.main`
- `terminal.shell`
- `media.shell`
- `plugin.host_shell`
- `overlay.dialog`
- `overlay.sheet`
- `overlay.menu`

## 旧实现

浮动聊天、权限 overlay、browser、WebChat 和 ToolPkg host 共享 active Material projection，但没有选择各自的 V2 surface。大部分 dialog、sheet 与 menu 直接使用 Material 视觉。

## 实施步骤

1. 为 detached Compose host 提供 `ThemeSurfaceHostV2` 外层，显式绑定对应 surface。
2. 为 Operit 的 dialog/sheet/menu 建立 V2 visual wrapper；业务弹层继续提供内容、状态和事件。
3. 为 browser/terminal/media/plugin 提供 shell template，保留外部内容内部渲染权。
4. WebChat bridge 输出统一的 active package visual projection；Web DOM 由 WebChat 自身消耗，不让 Android scene DSL 描述网页业务。

## 验收

- 浮动聊天切换模式、权限请求、browser/terminal/media 外壳都可从 surface registry 追踪。
- 插件内页未被强制重绘，但其标题、loading、error、容器与返回 chrome 随主题变化。
- overlay 的触控拦截、返回处理、焦点恢复和无障碍语义不变。

## 进展

[TODO] Detached hosts 接入。

[PARTIAL] `ThemeOverlaySurfaceHostV2` 试点已接入 WebSession pending dialog/browser sheet 与 `ChatToastHost`；generic Material dialog/sheet、menu、shared snackbar 和其余 overlay 待迁移，详见 [Overlay Primitive 试点](10_OverlayPrimitivesPilot.md)。
