---
fork_repository: https://github.com/AAswordman/Operit.git
---

# 原生 Tool Call 与 XML 协议兼容模式

当前 `enableToolCall` 已存在并已发布，但原生 provider 响应会先转换成 XML，再由统一执行链解析。这个过程会丢失 provider 的调用 ID、原始 arguments 和调用顺序信息。

本次沿用已发布的 `enableToolCall`：开启时使用原生 Tool Call 数据链路，关闭时保持 XML 协议兼容链路。原生链路首批接入 OpenAI Responses API（不含 Codex）和 DeepSeek Chat Completions。

作用域：

- 原生调用与结果的数据模型、流事件和历史记录
- OpenAI Responses API 与 DeepSeek Chat Completions 的流式及非流式解析
- 工具执行、权限结果、重试取消和下一轮请求的调用 ID 保持
- 已发布模型配置、ToolPkg API、设置界面、协议文档和回归测试

非作用域：

- Codex、Claude、Gemini、其他 OpenAI-compatible provider 的原生直通改造
- DeepSeek `/responses` endpoint
- 构建和测试命令；本轮只做静态检查

实施文档：

- [01 原生数据链路](01_native_data_flow.md)
- [02 Provider 适配](02_provider_adapters.md)
- [03 历史与兼容](03_history_and_compatibility.md)
- [04 测试与文档](04_tests_and_docs.md)

## 结果 [DONE]

本次改动已完成代码、持久化、兼容文档和针对性测试文件；按要求未触发构建或测试。
