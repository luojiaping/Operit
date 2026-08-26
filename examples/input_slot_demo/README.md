# 输入区插槽示例 / Input Slot Demo

这个 ToolPkg 演示如何向 Operit 聊天输入区注册三个 UI 插槽：

- `above_input`：输入框上方
- `input_drawer`：输入框容器内部、输入控件上方
- `input_toolbar_right`：输入工具栏右侧

This ToolPkg demonstrates the three chat input UI slots exposed by Operit:

- `above_input`: above the input control
- `input_drawer`: inside the input container, above the input control
- `input_toolbar_right`: on the right side of the input toolbar

注册函数是 `ToolPkg.registerInputSlotPlugin()`。Hook 可以返回普通字符串、
包含 `text`/`content` 的对象，或包含 `composeDsl` 的对象。Compose DSL screen
路径必须位于当前 ToolPkg 归档内。

The registration function is `ToolPkg.registerInputSlotPlugin()`. A hook can
return a string, an object containing `text`/`content`, or an object containing
`composeDsl`. A Compose DSL screen path must be inside the current ToolPkg archive.

本示例不需要宿主 capability 声明。导入并启用后，打开聊天页即可看到三个示例
插槽；也可以使用本地 hook runner 重放：

This example does not require a host capability declaration. After importing and
enabling it, open a chat to see the three demo slots. You can also replay a hook
with the local runner:

```sh
node tools/toolpkg/toolpkg_hook_runner.js \
  --source examples/input_slot_demo \
  --kind input_slot \
  --event render \
  --payload '{"slot":"above_input","chatId":"demo-chat","inputStyle":"classic"}' \
  --pretty
```
