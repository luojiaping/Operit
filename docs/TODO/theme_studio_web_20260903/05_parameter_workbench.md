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

[TODO] 1. 值模型 sealed union + 每控件"值转换器"（控件事件 → 合法 `ThemeParameterValueV2`），禁兜底与断言规避。

[TODO] 2. 分组/排序/可见性求值（visibleWhen 三条件）+ 行组件（label/description 本地化解析、默认态标记）。

[TODO] 3. 9 类控件 UI 组件（复用/抽取 preview 现有控件基础设施，新增 pair/layout/insets/radius）。

[TODO] 4. 工作台布局替换 `ThemeStudioPanel`：左编辑右预览（或按设备框布局），编辑项即时入 reducer → 预览桥。

[TODO] 5. 差异摘要与重置能力。

## 旧实现

硬编码控件、旧派生、无 AUTHOR 分区、无条件显隐。
