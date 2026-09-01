# 参数契约与运行时

## 旧实现

`ThemeParameterDefinitionV2` 只有 type、defaultValue 与 label。`ResolvedThemeParametersV2` 被注入 Compose local，却没有生产消费端。任意 `COLOR` 参数只能写入 DataStore，不能改变真实视觉。

## 新契约

schema 3 的参数定义声明：

1. 值类型：color、image_uri。
2. 控件：color palette、image picker。
3. effect：accent palette、token color、stage image。

effect 不接受 JSON path、任意节点引用、网络 URL 或包外资源 ID。color effect 只能修改已声明为 color 的 token；stage image 只能定位到注册为 scene 的日常 surface。

链接器保存 effect 的声明 package coordinate。创建 UI runtime 时解析当前 instance 值，建立不可变 token overlay 与 stage image overlay；不得修改 `LinkedThemeRuntimeV2` 或已安装归档文件。

## 最小功能单元

[DONE] 1. 扩展参数模型、严格 archive/linker 校验和选择值校验。schema 3 只公开 color/image URI；schema 2 selection 在 DataStore 迁移时清除，避免已删除的 string 参数值进入严格解码。

[DONE] 2. 建立 accent palette token overlay，并让 Material、component skin 与 scene 共用它。用户选色直接成为 primary，其余 accent role 从种子派生；linker 拒绝不透明度、重复 Material target 或 inherited target 冲突。

[DONE] 3. 建立受限的 URI stage image overlay，并接入 app.shell/chat.main scene host。URI 在 MIME、字节数和像素约束后取得持久读取权限；installer mutation gate 将 image grant journal、激活、替换、重置和对账串行化，避免旧选择或无主授权残留。

## 验收

- 参数类型、控件和 effect 的组合不合法时 archive/linker 拒绝安装。
- accent color 改变 Material primary、input focused border、button、message 和 scene token。
- image URI 只可用于声明的 scene stage，且不改变宿主槽位、滚动、IME、焦点或语义所有权。
- 基底参数不在 child package 激活时隐式改写 child 的作者视觉。
