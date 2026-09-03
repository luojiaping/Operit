# 03 空白包新建

## 现状

无"新建包"入口；创作只能由导入已有包开始。

## 意图

提供"新建空白包"向导：以选定基础包（内置 default `3.0.0`、内置 cyber `3.0.0`、或已导入包）为 `basis`，生成一份**合法派生包**（复制 basis 全量 parameters 定义，packageId/version/displayName/author/description 由作者填写），作者直接进入参数编辑，无需从零写 manifest。

## 期待

- 内置基础模板：default `3.0.0` 与 cyber `3.0.0` archive 作为 web-chat 静态资产随构建分发（来源：app 内置 asset，SHA 已锁定 `5a96abf5…` / `77c65c81…`）；列表展示各自基础信息。
- 新建向导：选基础包 → 表单（packageId 校验 `^[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+$`、version 严格 semver、displayName 必填含 `*`、description/author 可选）→ 生成编辑态。
- 派生语义：
  - 生成包 `basis = {packageId, version, archiveSha256}`（基础包坐标）。
  - parameters = 基础包 definitions 全量复制（保留 type/control/effects/visibility/section/order/visibleWhen），值状态初始=各默认值。
  - 作者编辑值在导出时写入对应 definition 的 `defaultValue`（非 URI）或仅作提示（URI，见 07）。
- 新建后立即进入 05 工作台；编辑期间修改 packageId/version 不破坏 basis 引用。

## 最小功能单元

[TODO] 1. 内置模板资产：default/cyber `3.0.0` 随 vite 静态资产分发 + 元信息索引（避免重复打包哈希计算）。

[TODO] 2. 新建向导表单与校验（命名规则、必填项、重复 packageId 仅提示）。

[TODO] 3. 派生实现：basis 锁定 + parameters 全量复制 + `StudioPackage` 生成；测试校验产物过 `validation.ts`。

## 旧实现

无。
