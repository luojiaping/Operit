---
title: Web 预览创作器 Preview Studio
fork: local worktree feat/web-preview-studio（自 fix/api-interface@b8cc583e 建工作树）
status: in_progress
---

# Web 预览创作器 Preview Studio

## 当前状况

fix/api-interface 分支开放了聊天输入区 UI 插槽接口，ToolPkg 插件可以通过
`registerInputSlotPlugin` 在三个宿主插槽渲染文本或 Compose DSL screen。但普通用户
自定义插件需要写 JavaScript、构造 manifest、打 ZIP 并学习 Compose DSL，门槛很高。

仓库内 web-chat 是 Android 聊天界面的 React 复刻，主题已走
snapshot 转 CSS 变量体系，但它只能在连接真机 HTTP 后端时使用，没有任何 mock
或预览设施，且与 app 实际渲染存在视觉与功能差异。

## 目标

- 从 web-chat 抽出可独立运行的模拟器外壳，无真机后端也可完整预览
- 在浏览器里实时预览输入插槽插件：粘贴代码或上传 .toolpkg
- 提供低代码模板，填表单即可生成可被 app 正常导入的 .toolpkg
- 修复 web-chat 与 app 之间影响预览正确性的主题渲染差异
- 预览站部署在本机 docker

## 边界

- 不改动 app 侧任何代码。app 侧 WebThemeSnapshot 协议字段扩展列为后续跟进项，
  由本 TODO 的 01 文档清单承载
- 差异修复只做 web 侧：类型加可选字段、渲染逻辑对齐；真机旧快照缺字段时按
  app 默认值渲染，该默认值来自 ThemePreferenceSnapshot.defaultVisual 的对应项
- Compose DSL 渲染器为白名单子集，预览近似而非 1:1，差异在文档标注

## 步骤

1. [DONE: web 侧主题类型扩展与渲染对齐](./01_fidelity_alignment.md)
2. [DONE: transport 抽象与 MockTransport](./02_mock_transport.md)
3. [DONE: SimulatorShell 与多入口构建、docker 部署](./03_simulator_shell.md)
4. [DONE: 输入插槽注入与 Compose DSL 渲染器](./04_slot_dsl_renderer.md)
5. [DONE: 代码预览模式](./05_code_preview.md)
6. [DONE: 低代码模板与 .toolpkg 导出](./06_template_export.md)
7. [DONE: CI、测试与文档收尾](./07_ci_tests_docs.md)

## 验收

- mock 模式下预览站全流程可用，不依赖真机
- 模板生成的 .toolpkg 能被 app 的 PackageManager 导入并在输入区渲染
- docker 容器本机可访问

## 完成记录

- 本地 worktree 分支 feat/web-preview-studio（自 fix/api-interface@b8cc583e）
- docker：web-chat/docker-compose.yml，容器 operit-preview-studio，本机 8447 端口，
  访问 http://127.0.0.1:8447/preview.html
- 已验证：mock 主题三场景渲染、消息统计条、SSE 流式、三插槽注入、
  DSL 渲染、粘贴代码应用、模板生成预览、.toolpkg 上传解析闭环、
  下载包可被标准 unzip 解析且与 examples/input_slot_demo 同构、
  vitest 25 项全部通过
- CI：pr-check.yml 的 Check WebChat 增加 npm test
- 文档：docs/doc-src/dev-core/web-preview-studio.md、BUILDING.md 预览站章节
- 重构：预览站改为主题工作室布局——左侧主题面板（明暗/主辅色/背景图片上传与
  模糊/气泡/风格/字号），右侧 devices.css 的 Google Pixel 6 Pro 手机壳内实时
  渲染（壳随舞台自适应缩放）；插件创作收为「插件工坊」次级模式
- 待真机验证：模板生成与上传演示的 .toolpkg 导入 app 后在输入区渲染
