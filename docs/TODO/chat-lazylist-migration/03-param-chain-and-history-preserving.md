# 03 参数链切换与旧历史滚动位置保持

## 旧实现

- `AIChatScreen:408 rememberScrollState()` 创建，经 `ChatScreenContent`
  传入 ChatArea / ChatScrollNavigator。
- `AIChatScreen:680` 发送消息时 `scrollState.animateScrollTo(maxValue)`。
- 加载更旧历史：ScrollState.maxValue 随内容增高而变，依赖锚点近似保位。

## 新实现

- `AIChatScreen` 改 `rememberLazyListState()`；`ChatScreenContent` 与
  `ChatArea` 的 `scrollState` 参数类型改 `LazyListState`。
- 发送消息滚动到底：`animateScrollToItem(totalItemsCount - 1)`。
- 旧历史保位：触发加载更旧窗口前记录
  `(firstVisibleItemKey, firstVisibleScrollOffset)`；chatHistory 头部扩展后
  `scrollToItem(新列表中该 key 的 index, 保存的 offset)`。key 为
  `Pair(timestamp, occurrence)`，头部插入不改既有 key，index 差即新增条数。

## 验证

- 点击"加载更旧历史"后视口首条消息视觉位置不跳变。
- 发送新消息（自动滚动开启时）滚到底；关闭时不打断阅读位置。

[DONE]
