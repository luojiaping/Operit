# Tool Call 与 XML 内部协议说明（项目现状）

本文档用于明确本项目在 **原生 Tool Call** 与 **XML 协议兼容** 两种配置下的真实行为。

> 结论先行：
> - `enableToolCall` 是模型配置中的二态协议开关。
> - 支持原生模式的 provider 通过结构化调用事件进入执行链，原始调用 ID 和 arguments 会被保留。
> - XML 仍作为兼容模式和显示结果格式使用，但不再是原生模式的 provider 响应中间格式。

---

## 1. 核心概念

### 1.1 两条内部通道

项目内部同时保留两种明确的工具调用通道：

- 原生通道：`NativeToolCall` / `NativeToolResult`，携带调用 ID、原始 JSON arguments 和结果关联
- XML 通道：使用 `<tool>` / `<tool_result>` 标签，服务于 XML 协议兼容模式和已有消息显示格式

- 工具调用（assistant 输出）：

```xml
<tool name="read_file">
<param name="path">README.md</param>
</tool>
```

- 工具结果（tool 角色写回历史）：

```xml
<tool_result name="read_file" status="success"><content>...</content></tool_result>
```

XML 模式的上层工具执行链（如 `EnhancedAIService` + `ToolExecutionManager`）按这个 XML 规范解析和执行。

### 1.2 Tool Call 开关的含义

`enableToolCall=true` 时，首批支持的 OpenAI Responses API 和 DeepSeek Chat Completions 会：

1. 在请求中发送 provider 原生工具定义和结构化工具历史
2. 从流式或非流式响应中保留原生调用记录
3. 直接将结构化调用交给工具执行链

`enableToolCall=false` 时，使用 XML 工具说明、XML 响应解析和 XML 工具结果历史。

Codex、Claude、Gemini 和其他 provider 本轮保持各自已有行为。

模型配置的新建默认值为 `true`，首次初始化的默认 DeepSeek 配置同样开启 Tool Call。已保存配置中的显式开关值不会因默认值调整而改变。

---

## 2. 不启用 Tool Call（enableToolCall = false）

### 2.1 请求阶段

- 请求体不注入 `tools` / `tool_choice`。
- 历史消息按普通 `role + content` 发送。
- 系统提示词保留 XML 工具说明与可用工具清单（由 `useToolCallApi=false` 分支控制）。

### 2.2 响应阶段

- 主要处理普通 `content`（及思考内容字段）。
- 不走 `tool_calls` 增量/非增量转换分支。
- 如果模型直接输出 XML 工具标签，上层会正常解析并执行。

---

## 3. 原生 Tool Call（enableToolCall = true）

### 3.1 生效前提

不是“开关一开就必生效”，还要求存在可用工具：

- `availableTools != null`
- `availableTools.isNotEmpty()`

在 `OpenAIProvider` 中通过 `effectiveEnableToolCall` 决定最终是否进入 Tool Call 模式。

### 3.2 请求阶段（结构化历史 -> Provider Tool Call）

### A. 注入工具定义

请求体会自动加入：

- `tools`: 由 `ToolPrompt` + 结构化参数构建的 JSON Schema
- `tool_choice`: `"auto"`

工具参数 schema 中的 `required` 固定输出为数组；无必填参数时输出空数组，避免兼容 OpenAI-style 校验器时被识别为 null。

### B. 历史消息转换

在 `buildMessagesAndCountTokens(..., useToolCall=true)` 中：

- 原生 assistant 历史中的 `NativeToolCall` 会被写入 `tool_calls` 数组。
- 原生结果历史中的 `NativeToolResult` 会被写成带原始 `tool_call_id` 的 `role="tool"` 消息。
- 没有结构化记录的已有 XML 历史仍按 XML 内容读取。

原生记录优先使用结构化数据；已有 XML 历史仍可被读取并转换为 provider 请求格式。

### 3.3 响应阶段（Provider Tool Call -> 结构化事件）

### A. 流式

当收到 `delta.tool_calls`（或 Responses API 的 function_call 事件）时：

- 增量参数在 provider 内部累积为原始 JSON 字符串
- 工具收尾时发出一个 `NATIVE_TOOL_CALL` 事件
- 上层不再从响应文本中提取该调用

### B. 非流式

当 `message.tool_calls` 或 Responses `output[].function_call` 存在时，直接发出结构化调用事件。

---

## 4. 统一执行链（两种模式共享）

两种模式共享工具权限和执行实现，但输入记录不同：

1. XML 模式由 `ToolExecutionManager.extractToolInvocations` 解析 XML。
2. 原生模式由 `ToolExecutionManager.createNativeToolInvocation` 接收结构化事件。
3. 两种模式都经过相同的权限、Hook、并行/串行执行链。
4. 原生模式的结果使用调用 ID写入 `NativeToolResult`，下一轮按 provider 原生格式发送。

---

## 5. 与系统提示词（System Prompt）的关系

`useToolCallApi` 会影响提示词呈现策略：

- `true`：使用更简化的工具说明，通常不再内嵌完整工具列表（因为 `tools` 已在请求体传入）。
- `false`：保留 XML 工具说明和可用工具列表。

未接入原生事件链路的 provider 继续使用各自已有的 XML 转换逻辑。

---

## 6. Provider 适配现状（当前代码语义）

- OpenAI Responses API（含通用 Responses 端点）：支持原生调用事件和结构化历史。
- DeepSeek Chat Completions：支持原生调用事件和 `reasoning_content` 历史。
- 其他 OpenAI-compatible provider：仍使用已有 XML ↔ Tool Call JSON 转换。
- `LLAMA_CPP`：
  - **非 ToolCall 模式**：仍是提示词约束的 XML 路径。
  - **ToolCall 模式**：除提示词外，已新增 JNI 原生 grammar 约束（`llama_sampler_init_grammar_lazy_patterns`），输出在 native 采样阶段被约束为 `tool_calls` 结构，再转换回项目内部 XML。
  - 上层执行链仍保持 XML 规范，不改执行器协议。
- `MNN`：当前仍未接入等价的原生 ToolCall 能力。

---

## 7. 一句话总结

本项目的 Tool Call 设计是：

- **按配置选择原生通道或 XML 兼容通道**；
- **原生通道保留 provider 调用身份和 arguments**；
- **两种通道共享工具执行、权限和结果展示逻辑**。
