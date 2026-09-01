# Input Primitive 试点

## 原状

`input` 是 V2 必需 component skin，默认和 Cyber Grid `2.1.0` 都已声明 normal、focused 和 error state。聊天 composer 已直接消费该 skin，但普通 Operit form/search 仍直接绘制 Material `OutlinedTextField`，导致非聊天输入框不显示 package frame 与 content color。

当前普通 field 包含 floating label、`TextFieldValue`、menu anchor、custom visual transformation、supporting/error text 与复杂焦点控制等不同契约。它们不能共用一个不加约束的 wrapper。

## 本批范围

1. 新增 `ThemeOutlinedTextFieldV2`，只支持无 floating label、`String` value 的 Material `OutlinedTextField`。
2. wrapper 用 input skin 绘制 container、content color、frame 与 elevation；Material field 继续持有实际编辑、cursor、selection、focus、IME、disabled、readOnly 和 accessibility semantics。
3. skin state 优先级固定为 disabled、error、focused、normal。wrapper 不添加 click、focusable、role 或 merged semantics。
4. 迁移三个真实生产入口：
   - WebSession JavaScript prompt dialog。
   - Memory search bar。
   - File Manager search dialog。
5. 对现有 caller 的 parent layout modifier 保留在 V2 outer surface；直接 Material modifier 单独保留给 focus requester、test tag 或 bring-into-view 需求。

## 明确不在范围

- 不支持 floating label、supporting text、prefix/suffix、menu anchor、`TextFieldValue`、`BasicTextField`、custom field color 或 custom shape。
- 不迁移聊天 composer、市场搜索、ExposedDropdownMenuBox、workflow/editor form、plugin DSL 或系统/recovery 页面。
- 不重写 input 行为、键盘动作、文本转换、selection、错误文案或 screen state。
- 不修改 `2.1.0` manifest、default archive、Cyber Grid basis 或版本坐标。

## 主题包契约

`input.focused` 与 `input.error` 已成为生产交互 state。archive validator 会拒绝直接声明 `input` 但缺少任一 state 的包；linker 会拒绝继承链合并后缺少任一 state 的主题。默认和 Cyber Grid `2.1.0` 现有归档已满足该契约，两个主题源的 `package.sh` 同步校验它。

## 验收

- 三个 production field 都通过 `input` skin 可见 package frame 与 content color。
- focused/error/disabled state 选择不改变 Material editable/disabled semantics 与稳定尺寸。
- Memory search 保持 `ImeAction.Search`、隐藏键盘及 search callback。
- WebSession prompt 与 File Manager search 保持原有确认、dismiss、checkbox 和 query callback。
- focused tests 覆盖 state priority、visual frame/content、editable semantics、文本替换和代表性 caller callback；本批不主动执行测试或构建。

## 最小功能单元

[DONE] 1. `ThemeOutlinedTextFieldV2` 与 input state/semantics test。

[DONE] 2. WebSession prompt 与 Memory search 接入。

[DONE] 3. File Manager search 接入、文档与静态校验。
