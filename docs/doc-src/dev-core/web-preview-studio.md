---
For_Agent: 本文档描述 Preview Studio 的结构、能力边界与部署方式
---

# Web Preview Studio

Preview Studio 是 Operit 聊天界面的浏览器预览与插件创作工具，从 web-chat 抽出的模拟器外壳驱动。
它服务于两个场景：

- 降低输入区插槽插件（`registerInputSlotPlugin`）的定制门槛：低代码模板或粘贴代码即可实时预览并导出 `.toolpkg`
- 在无真机环境下预览主题渲染差异（mock 主题快照携带 web 侧扩展字段）

## 入口与部署

构建为双入口：

- `index.html`：真机 web-chat 前端，构建后由 `npm run build:webchat` 同步进 APK assets，`preview.html` 及其入口 chunk/样式会被同步脚本排除
- `preview.html`：预览站，强制 `?mock=1` 模式，由 MockTransport 提供全部端点

本机 docker 部署：

```bash
cd web-chat
docker compose up -d --build
# 访问 http://127.0.0.1:8447/preview.html
```

端口 8447 避开真机 web-chat 服务的 8094。

## 目录结构

```
web-chat/src/
	preview/                     预览站专属（不进 APK assets）
		main.tsx                 预览入口与工具栏
		CodePreviewPanel.tsx     演示/粘贴/模板/上传四模式面板
		slotRunner.ts            main.js 注册执行与返回值解析
		templates.ts             低代码模板与 .toolpkg 源码生成
		toolpkgLoader.ts         ZIP 读取（store 与 deflate）
		zipWriter.ts             store ZIP 写入（导出 .toolpkg）
	ui/features/chat/
		composedsl/              Compose DSL 浏览器渲染器与插槽宿主
			composeDslRuntime.ts ctx 契约（UI 注册表、useState、MaterialTheme）
			ComposeDslRenderer.tsx  ComposeNode 到 React 的映射
			InputSlotHost.tsx     三个插槽的挂载点，PreviewSlotContext 注入
		util/
			chatTransport.ts     Transport 抽象（Real/Mock 同形状）
			mock/                MockTransport 与 fixtures
```

## Transport 抽象

`ChatTransportApi` 以 chatApi 模块形状为契约。`resolveChatTransport` 在
`?mock=1` 时返回 MockTransport，否则返回真机 fetch 实现。ViewModel 只感知
transport 实例。mock 状态是模块级单例，`previewControls` 暴露
`patchTheme` 与 `resetTheme` 给预览工具栏做实时主题覆盖。

## 插槽预览的执行模型

- screen 脚本是普通 JavaScript（`exports.default = function (ctx)`），
  浏览器用 `new Function` 执行，不需要 QuickJS
- ctx 契约对齐 app 的 `OperitComposeDslBridge`：`UI` 组件工厂、
  `useState(key, initial)`（state 的每个 key 即一个 useState key）、
  `MaterialTheme.colorScheme`
- 组件白名单：Card、Column、Row、Box、Spacer、Text、Button、Surface、Icon、
  Switch、LinearProgressIndicator。白名单外的组件显式报错
- 渲染为预览近似：dp 到 px 直接映射，布局与真机 Compose 存在细节差异，
  预览面板有固定提示
- 代码预览在主文档同源执行用户自己的代码；本地工具场景，无第三方注入面。
  沙箱 iframe 隔离是后续增强

## 主题快照的 web 扩展字段

真机旧快照缺失以下可选字段时，web 按 app 默认值渲染；app 侧下发为跟进项，
清单见 `docs/TODO/web_chat_preview_studio_20260831/01_fidelity_alignment.md`：

- `background.use_blur`、`background.blur_radius`、`background.muted`、`background.loop`
- `palette.tertiary_color`、`error_color`、`error_container_color`、
  `secondary_container_color`、`on_primary_color`、`on_secondary_color`、
  `surface_container_highest/low/lowest_color`
- `header.history_icon_color`、`header.pip_icon_color`
- `bubble.user_image/assistant_image` 的 crop、repeat、scale 与
  `tiled_nine_slice|nine_patch` 枚举
- `bubble.user_font/assistant_font` 气泡级独立字体
- 顶层 `on_color_mode`
- 消息的 `input_tokens`、`cached_input_tokens`、`output_tokens`、
  `wait_duration_ms`、`output_duration_ms`、`completed_at`、
  `variant_count`、`selected_variant_index`

## 测试

`npm --prefix web-chat run test` 覆盖：

- chatTheme 派生逻辑（M3 baseline、对比阈值 0.5、九宫格 border-image、气泡字体）
- MockTransport 端点行为与 RealTransport 形状一致性
- zipWriter 与 toolpkgLoader 读写闭环（store 与 deflate）
- slotRunner 注册解析与模板生成物执行

CI 在 web 变更时运行 typecheck、test 与 `npm run build:webchat`。
