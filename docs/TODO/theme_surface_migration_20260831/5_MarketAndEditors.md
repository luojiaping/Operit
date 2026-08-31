# 市场与编辑器

## 目标 surface

- `market.home`
- `market.category`
- `market.entry_detail`
- `market.publisher_console`
- `market.artifact_editor`
- `market.repository_editor`

## 页面范围

- Unified market 首页、分类、条目详情、评论、通知
- Publisher console
- Artifact publish/edit
- Repository、Skill、MCP publish/edit

## 介入方式

市场页面有高密度列表、tab、筛选、详情操作栏和长表单。页面使用 `TEMPLATE` 提供外层 page/frame，市场专用 layout 保留在原页面代码；列表、tab、表单、操作、dialog 和 sheet 使用 V2 primitive。

市场 surface 当前只允许 `TEMPLATE`。若未来需要独立背景、侧栏轨道或 package-owned slot 排列，必须先新增版本化 scene contract、host renderer 与 `ThemeSurfaceHostPolicyV2` 允许项，再升级 package declaration。默认不把网络加载、下拉刷新、评论分页和编辑状态放进主题 DSL。

## 验收

- 浏览、分类、详情、发布和管理页均有清晰 page/section/list hierarchy。
- tab、筛选、发布、删除与下载操作在 focus/disabled/selected 状态下可辨识。
- pull-to-refresh、lazy paging、评论输入、键盘和跳转保持原行为。

## 进展

[DONE] `MarketBrowseCard` 已由 `list_item` skin 绘制，市场浏览条目继承 package frame 与 content 色，同时保留详情 click 和安装操作。

[PARTIAL] `market.*` route 已进入基础 page host；通知、详情、编辑器、publisher console 的内部 card/form/action 仍待逐批迁移。
