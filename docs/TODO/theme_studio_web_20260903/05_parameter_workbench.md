# 05 manifest 驱动参数编辑

## 现状

`ThemeStudioPanel` 是硬编码控件（主题模式/主色/辅色/背景图/模糊/气泡/风格/字号），与 schema 4 参数控件+section+visibility+visibleWhen 无对应；AUTHOR 参数不可见；存在 `any`/`String(??)` 与 `as Error` 违规；颜色派生近似 app 旧基线。

## 意图

编辑面板完全由当前 `StudioPackage` 的 parameters 定义驱动：分组（section）、顺序（order）、可见性（visibility）、条件（visibleWhen）动态显隐，全部 9 类控件类型化渲染，值模型 sealed union，无兜底。

## 期待

- 分区渲染：APPEARANCE/CONVERSATION/COMPOSER/APP_CHROME；USER 默认展开、AUTHOR 折叠"作者级"区（可展开编辑）；每区按 order 排序。
- 条件求值：`boolean_equals`/`option_equals`/`resource_present` 作用于可见性（依赖值变化即时联动）。
- 控件类型化：
  - color_palette：preset + 自定义（ARGB 校验、alpha 必须 FF）+ "恢复包默认"。
  - color_pair_palette：light/dark 双通道。
  - toggle；choice（域=声明 options，与 target 域交集校验）；slider（min/max/step snap）。
  - image/video/font_picker：走素材库绑定（04）。
  - image_layout：crop/repeat/scale 可视化编辑（0..1 域、scale 0.1..8）。
  - insets：四边 0..96；corner_radius：0..96。
- 值状态：`Map<paramId, Value|unset>`；`unset` 显示为"使用包默认"（对 URI 为"未选择"）；单项重置 + 全包重置；修改计数展示。
- 参数值写入路径统一走 reducer（`studioEditorReducer`），控件只产生合法值；TypeScript 严格（无 `any`/`String(??)`/`as Record`）。
- 导出前差异摘要：列出与包默认不同的项（供创作者复查）。

## 最小功能单元

[DONE] 1. 值模型 sealed union + 每控件"值转换器"（控件事件 → 合法 `ThemeParameterValue`），禁兜底与断言规避（`editorState.ts` reducer）。

[DONE] 2. 分组/排序/可见性求值（visibleWhen 三条件）+ 行组件（label/description 本地化解析、默认态标记）。

[IN PROGRESS] 3. 9 类控件 UI 组件（复用/抽取 preview 现有控件基础设施，新增 pair/layout/insets/radius）：pair/layout/insets/radius 已进 AUTHOR 值编辑器；image/video/font_picker 走素材库。

[DONE] 4. 工作台布局替换 `ThemeStudioPanel`：左手机预览、右 `ThemeWorkbench`（`ThemeStudioPanel` 仍作为未导入包时的旧简化面板保留，见遗留）。

[IN PROGRESS] 5. 差异摘要与重置能力：单项重置/全包重置/`describeOverrides` 已有；导出前差异摘要 UI 未接。

## 旧实现

硬编码控件、旧派生、无 AUTHOR 分区、无条件显隐。

## 本轮进展

增加了按聊天组件定位的 Inspector 目标目录；USER 参数按目标过滤，AUTHOR 的 FLOAT/INSETS/IMAGE_LAYOUT/CORNER_RADIUS 等值类型可直接编辑，componentSkins 的状态、token、frame、内距和阴影参数进入同一编辑会话。

## 遗留

- 未导入包时 `main.tsx` 仍渲染旧 `ThemeStudioPanel`（硬编码控件），用户首屏看不到 schema 4 工作台。
- 目标过滤 `parameterBelongsToTarget` 基于参数 id/effects 文本匹配，参数命名偏离关键词时会漏显。
- 内置 default/cyber 包仅 14 个参数，大量 presentation target（气泡颜色/文字颜色/玻璃/图片布局/字体/Header/导航等）未注册参数，导致对应目标无可调项。
- 气泡/组件透明度无 schema 接口（`ThemeComponentSkin` 无 opacity 字段）。
- componentSkins 编辑的 frame 类型/描边/非 normal 状态不会被 runtime 投影到预览（见 06 遗留）。
