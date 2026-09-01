# 01 ChatArea：Column+verticalScroll → LazyColumn

## 旧实现

- `Column(Modifier.verticalScroll(scrollState))` 内 `forEachIndexed` 组合全部
  消息；`key(timestamp, occurrence)` 仅用于重组复用，不提供懒加载。
- 每项 `onGloballyPositioned` 写 `messageAnchors[timestamp]`，供跳转与
  视口中心计算（`resolveCenteredMessageIndex`）使用。
- 头部"加载更旧历史"、尾部"加载更新历史"、loading indicator 均为 Column
  直接子级。
- `scrollState: ScrollState` 为外部注入参数（AIChatScreen 创建）。

## 新实现

- `LazyColumn(state = lazyListState, contentPadding = PaddingValues(top, bottom))`：
  - item 0（条件）：加载更旧历史入口
  - `items(chatHistory, key = { Pair(timestamp, occurrence) })`：消息项
  - 尾部条件 item：加载更新历史、loading indicator、收尾 spacer
- 删除 `messageAnchors` / `pendingTargetAnchor` / 锚点清理 LaunchedEffect /
  项级 `onGloballyPositioned` 记录。
- `pendingJumpToMessageTimestamp` 保留（timestamp 是跨窗口分页的稳定标识），
  跳转 LaunchedEffect 改为：定位 index 后
  `lazyListState.animateScrollToItem(lazyIndex)`；目标为最新消息时滚到
  `totalItemsCount - 1`。
- `viewportHeightPx` 记录删除（LazyListState.layoutInfo 自带视口信息）。

## 验证

- 烟雾：50 条窗口会话滚动流畅、启动无 1s 级主线程告警。
- 跳转语义：locator 跳转任意消息、回复引用跳转、自动滚动到底均生效。

[DONE]
