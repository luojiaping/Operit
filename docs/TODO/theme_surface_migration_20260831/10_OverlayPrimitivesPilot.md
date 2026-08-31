# Overlay Primitive 试点

## 原状

`dialog`、`sheet`、`snackbar` 已是 V2 必需 component skin，默认和 Cyber Grid `2.1.0` 包均有 normal skin，但尚未有生产调用方。`overlay.dialog`、`overlay.sheet` 和 `overlay.toast` 已登记为 daily surface，却没有将 surface 所有权连接到对应 component skin 的宿主。

WebSession 的底部面板和 JavaScript pending dialog 使用手写 overlay window。它不能改用 `ModalBottomSheet` 或 `Dialog`，因为该窗口没有有效的 activity token。聊天 toast 已有稳定的超时、滚动和关闭队列，但仍直接绘制 Material `Surface`。

## 本批范围

1. 新增仅负责 package visual 的 overlay host：
   - `overlay.dialog` -> `dialog`
   - `overlay.sheet` -> `sheet`
   - `overlay.toast` -> `snackbar`
2. host 必须读取并校验 V2 surface implementation；不创建窗口、不接管 dismiss、焦点、返回键、手势或无障碍语义。
3. 用该 host 迁移 WebSession 手写 sheet 和 pending dialog 的 body；保留已有 scrim、点击关闭、IME/navigation inset、窗口层级和 route state。
4. 用该 host 迁移 `ChatToastHost` 的 body；保留动画、队列计时、滚动、关闭按钮及尺寸约束。
5. 移除 WebSession sheet scaffold 内会遮蔽 parent sheet skin 的硬编码背景；不在本批迁移 sheet 内的 item/empty state。

## 明确不在范围

- 不批量替换 Material `AlertDialog`、`ModalBottomSheet`、`DropdownMenu`、`Popup` 或 Android `Toast`。
- 不迁移 plugin DSL、WebView DOM、系统 dialog、IME 或无 activity token 之外的窗口策略。
- 不添加 `status.error`、新的 skin state 或 component ID，因此不重打 `2.1.0` 主题归档。
- 不改变 V1 renderer contract、公共 Compose 参数或 overlay 行为。

## 验收

- 三个 overlay surface 都经由匹配的 V2 component skin 消费。
- WebSession sheet/dialog 和 chat toast 在 default/cyber runtime 下可见 package frame 与 content color。
- 点击 scrim、确认/取消、toast 关闭、toast 自动超时、sheet 内容滚动和 IME padding 的所有权维持在原调用方。
- focused tests 覆盖 surface-to-component 映射和 child semantics；本批不主动执行测试或构建。

## 最小功能单元

[DONE] 1. overlay surface-to-component host 与 unit test。

[DONE] 2. WebSession sheet/dialog body 接入。

[DONE] 3. Chat toast body 接入与文档/静态校验。
