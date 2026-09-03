# 01 分支与共享主题库

## 现状

`feat/web-preview-studio` 的 `chatTheme.ts`/`chatTypes.ts` 是旧模型（M3_BASELINE、`image_scale`、`background.type/asset_url` 平铺）；`feat/plugin-interface` 的 web 层已是 schema 4（`background{stage,media}`、`user_font/assistant_font`、avatars、严格 fit 映射），且与 app 侧 `WebChatHttpBridge` 输出同构。

## 意图

以 plugin-interface 的 schema 4 web 层为单一契约源，在本分支重建共享主题库（`web-chat/src/shared/theme/`）：manifest 模型镜像、strict 解码/校验、派生 runtime。web 实机与 preview iframe 共用同一份，杜绝双套派生分叉。

## 期待

- `shared/theme/manifest.ts`：`ThemePackageManifestV2` 全量类型镜像：
  - 参数值 sealed union：color/color_pair/boolean/option/float/image_uri/video_uri/font_uri/image_layout/insets/corner_radius（字段、范围、`content://`、member ID 正则与 app 一致）。
  - 控件：color_palette/color_pair_palette/toggle/choice/slider/image_picker/video_picker/font_picker/author_value。
  - 参数定义：type/effects/control/defaultValue/visibility/section/order/visibleWhen。
  - effects：accent_palette/token_color/token_color_pair/stage_image/typography_scale/shape_scale/component_frame_scale/component_content_insets/presentation（62 个 typed targets 及 per-target 域）。
  - behavior 五段精确字段；assets/scenes/surfaces/materials（36 role 表）/tokens/variants/basis/contributions。
- `shared/theme/validation.ts`：镜像 app validator——control/type、effect/type、USER 需 default+section+非 author 控件、choice 默认∈选项、option⊆target 域、condition 依赖类型（boolean_equals→BOOLEAN、option_equals→OPTION 且选项存在、resource_present→URI 参数）、URI 参数无默认、asset 预算（128MB/512 entries/64MB/48MB 单条/ratio≤100/路径安全/SHA 格式/byteSize>0）、ZIP comment == `Operit Theme Package`、`operit-theme.json` 必须 ZIP 根。
- `shared/theme/typing.ts`：strict JSON 解码器（未知键拒绝、默认值语义与 kotlinx `explicitNulls=false` 一致），错误聚合为结构化诊断。
- `WebThemeSnapshot` DTO 保持与 `WebChatModels.kt` 一致，不另造副本。

## 最小功能单元

[DONE] 1. 从 `feat/plugin-interface` 迁移 schema 4 版 `chatTheme.ts`/`chatTypes.ts`（含 fit 校验、font-face 三族、stage/media 透明链）替代旧模型：9 个共享文件整体接入，studio 侧消费点（ThemeStudioPanel/ThemeSettingsPage/mock fixtures/composeDslRuntime/预览入口）同步迁移；`previewPalette`/`baseScheme` 作为共享派生模块；`npm run typecheck` 通过。

[DONE] 2. 实现 `shared/theme/manifest.ts` 模型镜像（值/控件/效果/behavior/assets/scene/material 类型镜像），strict 解码逐字段校验（未知键/类型/值域/ARGB/content:///crop/insets/behavior 精确字段集），无 `any`。

[DONE] 3. 实现 `shared/theme/validation.ts` 全约束校验（control/type、effect/type、USER 默认+section、AUTHOR 必须 author_value、choice 默认∈选项、option⊆target 域、condition 依赖类型、SCENE surface 场景引用、assets 预算/SHA/路径、archive 预算与 comment/根条目），错误带字段路径。

[DONE] 4. 对齐 test：`themeManifest.test.ts` 17 项 strict 解码/语义/预算正反样例；全部 43 项 vitest 通过。

## 旧实现

无共享库；studio 自带旧模型，app 内另有一套 schema 4 实现。

## 迁移影响

`chatTypes.ts` 字段名前缀 `background.type` 等被替换为 `background.stage/media`；`ThemeStudioPanel`/mock 依赖旧字段处一并迁移（见 05/06）。
