# 历史与兼容

已发布的 `enableToolCall` 字段继续作为配置入口：

- `true`：本次支持 provider 使用原生 Tool Call
- `false`：使用 XML 协议兼容模式
- 已保存配置值不改写

原生调用记录需要进入当前回合的 `PromptTurn`，并在工具结果回传时携带调用 ID。为了支持重新打开会话、消息变体和聊天导出，消息持久化层保存结构化协议元数据；已有消息没有该元数据时仍按已有 XML 历史读取。

历史请求必须满足：

- Chat Completions 的 assistant `tool_calls` 与 `role=tool` 使用同一调用 ID
- Responses 的 `function_call` 与 `function_call_output` 使用同一 `call_id`
- Responses 的调用、结果相邻顺序不被可见文本或结果聚合打乱

## 结果 [DONE]

模型消息、Room 消息/变体和聊天归档已保存结构化协议元数据，并添加 v21 到 v22 数据库迁移。
