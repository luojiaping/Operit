# 测试与文档

测试覆盖：

- OpenAI Responses 流式和非流式调用解析
- DeepSeek Chat Completions 流式和非流式调用解析
- 原始 ID、arguments、多个调用及结果顺序
- 原生调用事件到工具执行结果的 ID 传递
- 原生调用展示名称、参数、分组数量和历史消息恢复
- 原生模式与 XML 模式的请求体回归
- 配置 JSON、Room 消息元数据和已发布 ToolPkg 字段兼容

文档同步：

- 更新 `toolcall_xml_conversion.md` 的模式说明
- 更新模型设置和 ToolPkg 类型说明
- 更新设置界面文案，明确原生模式与 XML 兼容模式

## 结果 [DONE]

已补充原生解析、Responses 历史顺序、元数据、工具执行和流事件测试，并同步公开类型与协议文档。
