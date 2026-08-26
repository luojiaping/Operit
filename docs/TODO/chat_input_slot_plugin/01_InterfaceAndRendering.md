---
title: 接口、注册管线和 renderer
status: in_progress
---

# 接口、注册管线和 renderer

## 接口

ToolPkg 使用：

```ts
ToolPkg.registerInputSlotPlugin({
  id: "status_slot",
  slot: "above_input",
  function(event) {
    return {
      composeDsl: {
        screen: "dist/ui/status/index.ui.js",
        state: { chatId: event.eventPayload.chatId }
      }
    };
  }
});
```

`slot` 支持 `above_input`、`input_drawer` 和 `input_toolbar_right`。Hook 的
`eventName` 固定为 `render`，事件 payload 包含 slot、chatId、runtime、inputStyle、
isProcessing、isInputFocused 和 inputText。

Hook 返回值支持：

- 非空字符串，作为普通文本渲染
- `{ text: string }` 或 `{ content: string }`
- `{ composeDsl: { screen, state?, memo?, moduleSpec? } }`
- `null` 或 `void`，表示该次不渲染内容

## 宿主执行

- 宿主只同步当前已启用 ToolPkg 的插槽注册项
- 多个 ToolPkg 的结果按 slot、包名和注册 ID 的稳定顺序渲染
- 每个 slot 使用共享的前置 Hook 超时预算
- Hook 失败和超时只记录日志，不阻断输入框本身
- `inputText` 会传入 payload，但输入文字变化不单独触发 Hook 解析；slot 内容
  由聊天 ID、输入样式、处理状态、焦点状态和注册版本驱动更新
- Compose DSL 复用 XmlRenderPluginRegistry 的执行上下文和生命周期释放逻辑

## 接入点

- Agent 输入区的普通和透明布局都提供三个 slot
- Classic 输入区提供三个 slot
- `inputStyle` 分别为 `agent` 和 `classic`
- `runtime` 使用当前输入菜单 runtime

## 完成记录

- `ChatInputSlotPluginRegistry` 和 `ToolPkgInputSlotBridge` 已创建
- ToolPkg main registration、parser、runtime 和类型声明已接入
- Agent 和 Classic 输入区的三个 slot 已接入
- `toolpkg_hook_runner.js` 已支持 `input_slot` 重放
- `examples/input_slot_demo` 已提供文本和 Compose DSL 示例
- 文档已接入 `package-dev/toolpkg.md` 和 `TOOLPKG_FORMAT_GUIDE.md`
- `git diff --check`、示例 manifest JSON 和 src/dist 一致性检查通过
- 远程 `build_release` 已通过，宿主提交 `cd33dce5c`
- 待完成：设备验证
