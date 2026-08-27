# Provider 适配

首批范围：

- OpenAI Responses API：处理 `function_call`、参数增量和 `function_call_output`
- DeepSeek Chat Completions：处理 `choices[].delta.tool_calls`、非流式 `message.tool_calls` 和 `role=tool`

两类 provider 都需要：

- 流式参数分片收集后发出一个完整原生调用事件
- 非流式响应直接产生原生调用记录
- 请求历史使用原始调用 ID 和 arguments
- 多调用结果按 ID 绑定，不按完成时间绑定

Codex 保持现有实现，不因复用 Responses provider 基类而进入本次 native 分支。

## 结果 [DONE]

OpenAI Responses API 和 DeepSeek Chat Completions 已接入流式、非流式原生调用事件及原始调用 ID 回传。
