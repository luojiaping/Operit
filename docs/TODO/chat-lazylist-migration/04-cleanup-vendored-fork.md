# 04 清理 vendored components/lazy fork

## 旧实现

- `app/src/main/java/com/ai/assistance/operit/ui/features/chat/components/lazy/`
  为未接线的 RecyclerLazyColumn fork（双向索引、LayoutInfo 等大量 vendored
  代码），仅被 ChatScrollNavigator/ScrollToBottomButton 的 fork 重载引用；
  路线 A 迁移完成后引用归零。

## 新实现

- 删除 fork 重载后全仓检索确认无残余 import，整目录移除。
- 独立 PR（本迁移验证通过后执行），保持主迁移 PR 的可回溯性。

## 验证

- 全仓 `rg "components.lazy|RecyclerLazyColumn"` 无结果；构建通过。

[DONE]
