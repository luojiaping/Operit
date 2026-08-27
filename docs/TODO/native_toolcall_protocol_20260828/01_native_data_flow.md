# 原生数据链路

旧实现：

- provider 将原生 `tool_calls` 或 Responses `function_call` 转成 XML 文本
- `EnhancedAIService` 只从文本中提取 XML 工具调用
- `ToolInvocation` 和 `ToolResult` 没有稳定的原生调用 ID

意图修正：

- 增加可携带调用 ID、工具名、原始 JSON arguments 和顺序的原生调用记录
- 通过现有可重放流事件通道传递完整调用事件
- 原生模式直接进入现有权限、Hook、并行执行链
- XML 模式保持现有文本解析行为

预期结果：

- 原生调用不经过 provider 层的 XML 转换
- 多工具调用按原始顺序执行并保留各自 ID
- 权限拒绝、Hook 拦截、工具不存在和执行错误都绑定原始调用 ID
- 原生调用在消息展示层投影为既有工具行，执行和 provider 历史仍使用结构化记录

## 结果 [DONE]

已增加原生调用/结果模型、流事件和执行层入口；XML 模式继续使用原有解析链路。
