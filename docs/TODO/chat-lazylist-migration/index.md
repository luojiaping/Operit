---
For_Agent: 对项目大规模动工前按本规范协作
Fork: fix/waifu-chat-latency (worktree /home/work/Operit-waifu-latency)
---

# ChatArea 消息列表 LazyColumn 化（路线 A：官方 LazyColumn）

## 原本状况

- `ChatArea.kt`（1489 行）用 `Column + verticalScroll(scrollState)` 组合**全部**
  显示窗口消息（最多 50 条）：每条消息的 `MessageItem`（气泡/富文本/菜单）
  全量参与组合、测量与布局，无任何回收。
- 滚动定位依赖手写锚点机制：每个消息项 `onGloballyPositioned` 记录
  `messageAnchors[timestamp] = (absoluteTopPx, heightPx)`，跳转时
  `scrollState.animateScrollTo(absoluteTopPx)`。
- `ChatScrollNavigator.kt`（1295 行）存在两套重载：`ScrollState` 版（在用）
  与 `components.lazy.LazyListState`（vendored fork）版（未接线死代码）。
- 启动与长列表滚动时 Compose 主线程压力明显（真机 AnrMonitor 多次
  541-1662ms 告警，堆栈集中在消息列表首帧布局/测量）。

## 意图

把消息列表迁移到 Compose 官方 `LazyColumn` + `rememberLazyListState`：

- 仅组合可见项与预取项，消除长列表全量布局成本（启动 ANR、滚动掉帧）。
- 用 `LazyListState` 的 index/key 定位替换手写锚点 map。
- 删除未接线的 vendored `components/lazy` fork 死代码，降低维护面。

## 期待结果

- 消息数增长不再放大组合/布局开销；真机启动与滚动 ANR 告警消失或显著减少。
- 既有交互语义不变：自动滚动到底、用户上滑解除自动滚动、回到底部按钮、
  消息定位器（locator chip/dialog）、跳转指定消息、加载更旧/更新历史窗口。
- 锚点机制（`ChatScrollMessageAnchor`、`messageAnchors`）与 vendored fork
  一并移除，不留双轨。

## 大致作用域（PR 拆分）

1. `01-chat-area-lazycolumn.md` 核心迁移（与本文件同 PR）
2. `02-scroll-navigator-lazylist.md`（同 PR）
3. `03-param-chain-and-history-preserving.md`（同 PR）
4. `04-cleanup-vendored-fork.md`（独立 PR：删除 components/lazy）
