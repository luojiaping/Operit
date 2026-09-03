# 06 实时预览运行时与桥

## 现状

`ThemeStudioPanel` 在父页合成近似 `WebThemeSnapshot` 直接下发 iframe；mockTransport `setTheme` 按当前 chat 键写主题；桥仅校验 `type` 判别字段且存在 pending 缓存/模式过滤缺陷；颜色派生与 app `accentPaletteTokens`（HSL→20 role）不一致。

## 意图

派生子落 iframe 内同一 `runtime.ts`：父页只下发 `{manifest, values, darkTheme}`，iframe 运行时计算 `WebThemeSnapshot` 并喂给真实 `chatTheme.ts`；mock 层为无主题态兜底（不再自造主题）。派生常量与 app 侧逐项对齐。

## 期待

- `shared/theme/runtime.ts` 纯函数：
  - token pool：basis 链 token_projection + `token_color`/`token_color_pair` 覆盖；`accent_palette` 20 role 生成（`primarySaturation`/`secondary/tertiary` 饱和系数、`tone` 色相偏移、on 色 luminance 阈值——与 `ThemePackageUiRuntimeV2.kt:843-908` 完全一致）。
  - behavior→presentation values（`parameterValues()` 全 62 target 映射）；typography/shape scale 乘子（clamp 0.5..2 / 0..96）；frame scale/insets 变体。
  - background: stage image（fit/opacity）+ media（image/video/blur/muted/loop）→ `WebThemeSnapshot.background{stage,media}`；bubble/avatar/font/header/input 全字段。
  - 素材 resource → blob URL 注入（基于 04 素材库注册表）。
- 桥重构（`previewBridge`）：
  - 消息运行时校验（schema guard：`manifest`/`values`/`surface`/`mode` 字段级），非法消息丢弃并 console.error。
  - `event.source` 双侧过滤；ready 握手只回最后一次态；pending 缓存 flush 应用当前 mode 过滤（修 M3/M4）。
- mockTransport：主题键改为显式 `studio-theme`（或独立主题通道），不再依赖 chat 存在性；`getTheme` 无主题时返回不含 palette 覆盖的基础态（移除 chat-main 静默 fallback 与 deleted-chat 崩溃路径）。
- 预览态：编辑 → reducer 后 debounce（约 120ms）全量下发（值集合 json 可哈希短路径判定免更新）。

## 最小功能单元

[DONE] 1. `runtime.ts` 实现 + 对照测试（seed→20 role、behavior 映射、scale clamp、background 分派），`tests/runtime.test.ts` 通过。

[DONE] 2. iframe 内运行时接线：`resolveEditorRuntimeTheme` 在父页计算（asset:// 经素材库解析为 blob URL）后过桥，iframe 内跑真实 `chatTheme.ts`。

[IN PROGRESS] 3. 桥重构：目标选择/高亮双向消息（`preview-selection`/`preview-highlight`）已加；字段级 guard、source 过滤、pending mode 过滤、ready 重放未完。

[TODO] 4. mockTransport 主题通道显式化 + 无主题基础态 + 删除 chat 边界修复。

[TODO] 5. 编辑 → 预览 debounce 全链路 + 值变化 hash 短路。

## 旧实现

父页自造 snapshot（旧模型）、桥弱校验、mock 按 chat 键主题、派生不齐。

## 本轮进展

iframe 内的真实聊天 DOM 已加入稳定目标标识；点击后通过 `preview-selection` 通知父页并高亮目标，父页通过 `preview-highlight` 同步选中状态。runtime 已开始投影 componentSkins、组件 Insets、shape scale、字体资源和 App Chrome。

## 遗留

- runtime 仅投影 componentSkins 的 normal 态（container/content 色、圆角、阴影、内距）；frame 类型变体（cut_corners/hud_notched/corner_brackets/segmented_rail）、border/accent 描边、disabled/selected/focused/error 状态未投影，编辑后预览不变。
- 深浅色模式切换（当前固定 dark）未暴露。
- 桥的 pending 缓存/mode 过滤缺陷仍在（M3/M4）。
