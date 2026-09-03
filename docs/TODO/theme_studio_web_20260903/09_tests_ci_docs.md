# 09 测试/CI/文档

## 现状

studio 分支已有 vitest（`npm test`，24+ 项）但 tsconfig 未纳入 tests；CI 只跑 `npm test`，未跑 typecheck；`docs/doc-src/dev-core/web-preview-studio.md` 描述旧模型；`docs/TODO/web_chat_preview_studio_20260831/` 为旧计划。

## 意图

围绕 schema 4 契约重建测试面，CI 补齐 typecheck，文档同步；旧 TODO 标注 superseded。

## 期待

- 测试覆盖：
  - shared/theme：strict 解码、字段集精确、默认值语义；validation 全约束（每约束至少一正一反）；default/cyber `3.0.0` 真实 fixture。
  - runtime：accent 20 role 真值对照、behavior 映射、scale clamp、background/stage 分派、bubble/avatar/font DTO 形状。
  - 导入/新建：合法包进编辑态、畸形包聚合报错、白包派生通过校验。
  - 素材：mime/头签名/大小/重复 key；素材库 CRUD（IndexedDB mock/内存实现适配）。
  - 导出：zip 确定性（两次产物 SHA 相等）、comment、round-trip 导入、烘焙规则、未烘焙提示。
  - 工作台：条件显隐联动、控件范围、重置、差异摘要。
  - 桥：字段级 guard、source 过滤、pending mode 过滤、ready 重放、debounce。
- CI：pr-check 或 web-chat 工作流增加 `npm run typecheck`（与 `npm test` 并列）；`git diff --check` 由仓库门禁覆盖。
- 文档：
  - `docs/doc-src/dev-core/web-preview-studio.md` 重写为 schema 4 工作流说明（导入→素材→编辑→预览→导出）。
  - 本 TODO `index.md` 完成定义逐项标记。
  - 旧 `web_chat_preview_studio_20260831`（骨架期）标注为历史，保留其 shell/mock 复用说明。
  - `docs/TODO/README.md` 计划细化清单登记本批。

## 最小功能单元

[IN PROGRESS] 1. 测试面补齐：shared/theme（manifest/loader/runtime/exporter/editorState/assets）已有 100+ 项 vitest；工作台交互与桥 guard 用例未完。

[TODO] 2. CI 增加 typecheck 并验证。

[TODO] 3. 文档更新（dev-core 指南、旧 TODO 标注、index 状态、TODO README 登记）。

## 旧实现

vitest 24+ 项基于旧 snapshot 模型；无 typecheck 阀门；文档旧模型。
