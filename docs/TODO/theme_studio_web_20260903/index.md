---
title: Web 主题工作室（schema 4）
fork: https://github.com/luojiaping/Operit.git
branch: feat/theme-studio
status: in_progress
---

# Web 主题工作室（schema 4）

## 背景

`feat/web-preview-studio` 已建立 web 预备演示站：手机壳 iframe 实时预览、mock transport、AppShell/settings 复刻、插件工坊骨架、vitest 与 docker，可访问 `http://127.0.0.1:8447/preview.html`。但它内置的主题模型是旧的（accent/background/bubble/风格/字号近似），与当前 schema 4 契约（feat/plugin-interface 已发布 default/cyber `3.0.0`）不一致；parsing、校验、颜色派生、素材边界也未对齐。

本批目标：把该站升级为**主题工作室**——导入 `.otheme` → 素材上传 → manifest 驱动全参数编辑 → 实时预览 → 打包导出 `.otheme`，并支持直接新建空白包（以基础包为 `basis` 的派生子包），降低创作门槛。

## 已确认边界

- 新建分支 `feat/theme-studio`（自 `feat/web-preview-studio` 切出）。
- 以 `feat/plugin-interface` 分支的 schema 4 web 层（`chatTheme.ts`/`chatTypes.ts` 等）为契约源；**app 侧代码零改动**。
- 素材导出边界：URI 类参数（IMAGE/VIDEO/FONT_URI）在 schema 4 下必须 `content://` 且**无包默认**；本批素材只作为预览资源 + 导出为 `manifest.assets` 声明。是否做 app 侧 schema 扩展让素材可作包默认背景，本轮不做。
- 插件工坊（input-slot/toolpkg）本轮只保留壳并修正其契约硬错，不做全量 hook 仿真。
- 预览一致性：iframe 内跑与实机同一 `runtime.ts` 派生（manifest+值 `→` `WebThemeSnapshot`），复用现有 `chatTheme.ts` 渲染。
- 参数编辑覆盖全部 11 类值/9 控件，AUTHOR 参数在独立"作者级"分区可调。
- 不写回退/兜底代码；类型严格（禁 `any`/`unknown` 兜底/`String(??)`）。

## 目录

1. [分支与共享主题库](./01_branch_and_theme_library.md)
2. [包导入与解析校验](./02_package_import.md)
3. [空白包新建](./03_blank_package.md)
4. [素材管线](./04_assets_pipeline.md)
5. [manifest 驱动参数编辑](./05_parameter_workbench.md)
6. [实时预览运行时与桥](./06_live_preview.md)
7. [打包导出](./07_pack_export.md)
8. [预览壳修复](./08_shell_fixes.md)
9. [测试/CI/文档](./09_tests_ci_docs.md)

## 完成定义

- 导入 default/cyber `3.0.0` archive 可全链路通过并进入编辑态；畸形包按契约报错。
- 编辑面板由 manifest 全量参数驱动（section/order/visibility/visibleWhen 生效），AUTHOR 参数可调。
- 编辑期间属性即时反映到 iframe 内手机预览，派生规则与 app runtime 常量一致。
- 素材上传受控（mime/解码/字体头/字节上限），可被 URI 参数与预览引用，导出进入 `assets[]`。
- 导出 `.otheme` 可被本工具重新导入通过校验，ZIP comment/`operit-theme.json` 位置/字段集严格合规。
- 空白包向导生成以选定基础包为 basis 的合法派生包。
- `npm run typecheck` 与 `npm test` 通过且纳入 CI；文档与 CI 脚本随动。

## 当前进度（2026-09-04 快照）

已完成（详见各分篇“本轮进展”）：

- 01 共享主题库：schema 4 web 层迁移与 `shared/theme/manifest.ts` 镜像完成。
- 02 包导入：zipReader/packageLoader/ThemeStudioEntry 完成，default/cyber 真归档冒烟通过。
- 03 空白包：内置模板 + 派生实现完成。
- 04 素材：校验器、IndexedDB 素材库、目标 Inspector 内统一素材区完成（分类视图/预览细化未完）。
- 05 工作台：目标目录 + USER/AUTHOR 参数过滤编辑 + componentSkins 编辑会话完成（见遗留）。
- 06 预览：iframe 内目标点击选择/高亮桥、componentSkins/Insets/shape/font/chrome 投影完成。
- 07 导出：exporter + zipWriter 增强完成（deflate/comment/确定性/sha 回填/回环自检）。
- Docker 部署：`web-chat/Dockerfile` + `nginx.conf`（HTML 禁缓存），镜像 `web-chat-operit-preview-studio:13d80697` 已部署本机 `8447`，公网 `https://studio.xagc.xyz/preview.html?studio=theme-studio-v2`。

已知遗留（按用户验收反馈，优先级从高到低）：

1. 首屏仍是旧简化面板 `ThemeStudioPanel`：未导入包时用户看到的是硬编码控件（圆角/头像/用户气泡主色三个开关），不是 schema 4 工作台；完整 `ThemeWorkbench` 仅在导入/新建后出现。需要移除旧面板路径或以默认包直接进入工作台。
2. 内置 default/cyber 包参数覆盖不足：每包仅 14 个参数，气泡颜色/文字颜色/玻璃效果/图片布局/自定义字体/导航栏/Header/工具消息/Markdown/代码块等未注册为可调参数。
3. 组件与气泡无透明度接口：`ThemeComponentSkin`、`WebThemeComponentSkin` 均无 opacity/alpha 字段；`BACKGROUND_OPACITY` 仅作用于背景媒体。需 schema 扩展或带 alpha 颜色模型（涉及 app 侧，需先确认发布边界）。
4. componentSkins 编辑不完整投影：runtime 只投影 normal 态的圆角/阴影/内距，frame 类型（cut_corners/hud_notched/corner_brackets/segmented_rail）、border/accent 描边、disabled/selected/focused/error 状态编辑后不反映到预览。
5. 素材库未成为一级入口：上传/绑定藏在 Workbench 的素材区，无分类视图与缩略图/视频/字体预览。
6. 08 壳层遗留：窄屏单滚动容器、移动端验收未完。
7. 09 测试/CI 遗留：CI 增加 typecheck、dev-core 文档重写、旧 TODO 标注未做。

## 迁移说明

本分支（`feat/theme-studio`，HEAD `13d80697`）已推送至 GitHub fork。迁移时以该分支为源，`web-chat/` 为全部 web 服务代码；主题契约镜像在 `web-chat/src/shared/theme/`，studio UI 在 `web-chat/src/preview/`，部署入口为 `web-chat/Dockerfile` + `nginx.conf`（容器 `operit-preview-studio`，本机 `8447`）。
