# 02 ChatScrollNavigator：统一为官方 LazyListState 版

## 旧实现

- 两套重载并存：`ScrollState` 版（在用，基于 value/maxValue/锚点）与
  vendored fork `ChatLazyListState` 版（死代码，基于 layoutInfo）。
- 视口中心消息：`resolveCenteredMessageIndex(scrollState, viewportHeightPx,
  chatHistory, messageAnchors)` 用锚点 absoluteTopPx 与 value+viewport/2 比较。
- 底部判定：`scrollState.value >= scrollState.maxValue`。
- 拖拽/滚动会话：`interactionSource.collectIsDraggedAsState()` +
  `snapshotFlow { scrollState.value }`。

## 新实现

- 保留单一重载，签名 `scrollState: LazyListState`（官方
  `androidx.compose.foundation.lazy.LazyListState`）。
- 视口中心消息：`layoutInfo.visibleItemsInfo` 中过滤消息 key（Pair 类型），
  找覆盖 `viewportStart + viewportSize/2` 的项，从 key 取 timestamp 反查
  chatHistory index。
- 底部判定：`!scrollState.canScrollForward`（contentPadding 计入滚动范围，
  语义与 value>=maxValue 一致）+ `!hasNewerDisplayHistory`。
- 拖拽/滚动会话、chip 显隐节流逻辑保持不变（interactionSource 与
  isScrollInProgress 在 LazyListState 上同样可用）。
- `viewportHeightPx` 与 `messageAnchors` 参数删除。

## 验证

- locator chip 显示当前中心消息编号；上滑出现 chip、静止后延时隐藏；
  在底部时自动滚动恢复开关行为不变。

[DONE]
