# 实施计划与验收标准

## 阶段 0：冻结契约

- [ ] 确认公开 API 名称：`registerChatSubagentViewPlugin`
- [ ] 确认 ToolPkg 只返回卡片元数据，不返回 Compose DSL
- [ ] 确认宿主 Agent runtime 是 execution record 的唯一生产者
- [ ] 确认是否允许复制参数和结果
- [ ] 确认大参数、大结果的保存上限和显示策略

阶段 0 完成后再写入 `examples/types/toolpkg.d.ts`。在契约冻结前不要把设计草案伪装成公共类型。

## 阶段 1：数据模型和持久化

预计改动：

- `data/model/SubagentExecutionEntity.kt`
- `data/model/SubagentToolCallEntity.kt`
- `data/dao/SubagentExecutionDao.kt`
- `data/db/AppDatabase.kt`
- Room migration
- `data/repository/SubagentExecutionRepository.kt`
- `data/model/ChatMessage.kt` 或 ChatArea 的稳定父消息锚点

验收：

- 新建、更新、完成、失败、取消都能持久化
- 工具调用按 `sequence` 稳定排序
- 父聊天删除时关联记录有明确处理
- App 重启后可以查询历史执行记录

## 阶段 2：执行上下文和工具调用采集

预计改动：

- Agent 执行 loop
- `EnhancedAIService` 或未来独立 Agent runtime
- Tool invocation / result 传递链
- Tool Lifecycle payload

要求：

- 每个子代理有独立 `executionId`
- 每个工具调用有独立 `callId`
- 参数、结果、错误与状态变更使用同一 ID
- 不从 `ToolProgressBus` 或 XML 文本推断身份
- Agent 权限和 Prompt 上下文与 execution context 保持一致

## 阶段 3：宿主卡片与只读详情

预计改动：

- `plugins/chatview` 下新增 `ChatSubagentViewPluginRegistry.kt`
- `ui/features/chat/components/ChatArea.kt`
- 新增 `SubagentCard.kt`
- 新增 `SubagentReadonlyDetailScreen.kt` 或只读 Bottom Sheet
- ChatScreenContent / navigation 的只读入口

验收：

- 卡片只显示在对应父消息附近
- Cursor 和 Bubble 风格都能显示
- running 状态实时更新，完成/失败状态可回放
- 点击卡片只打开只读详情
- 详情页无输入框、发送、编辑、重试和执行按钮
- 返回操作不会改变子代理状态

## 阶段 4：ToolPkg 公共接口和 Bridge

预计改动：

- `ToolPkgCommonPluginConstants.kt`
- `JsToolPkgRegistration.kt`
- `JsEngine.kt`
- `ToolPkgMainRegistrationScriptParser.kt`
- `ToolPkgParser.kt`
- `ToolPkgHookBridgeSupport.kt`
- `ToolPkgCommonBridgePlugin.kt`
- 新增 `ToolPkgChatSubagentViewBridge.kt`
- `examples/types/toolpkg.d.ts`
- `examples/types/core.d.ts`
- `tools/toolpkg/toolpkg_hook_runner.js`

Bridge 行为：

1. 监听宿主 execution snapshot。
2. 为当前聊天和父消息筛选 execution。
3. 按插件注册项调用 `render`。
4. 只接受卡片 metadata。
5. 将点击行为交回宿主 Registry。

## 阶段 5：Plan/Build Agent 集成

这一步不属于本 UI 接口的首个实现，但必须保留扩展点：

- Plan/Build 使用 qualified Agent ID
- Agent 专属 Prompt 作为 system context 进入 execution
- Agent 权限同时影响模型可见工具和工具实际执行
- Plan -> Build 的切换绑定到后续会话轮次
- `maxSteps` 由 Agent loop 实际执行
- 记录中保存执行时的 Agent 元数据快照

不要先用普通 Prompt 文本模拟 Agent 隔离，再把卡片当作完成标志。

## 测试与验证

### 数据层

- Room migration 测试
- execution/tool call 状态转移测试
- 重复完成事件幂等测试
- 父聊天删除关联记录测试

### ToolPkg 层

- 注册 JSON 校验测试
- 多插件排序和超时测试
- execution snapshot payload 类型测试
- runner 能采集 `chat_subagent_view` 注册项

### UI 层

- 卡片挂载到正确父消息
- 工具调用实时更新和历史回放
- 详情页不存在 composer 和发送入口
- 旋转、重组、返回和重新打开不会重复创建执行记录
- Cursor/Bubble 两套风格均通过截图检查

### Agent 集成

- 不同 Agent 的 Prompt 不串线
- 不同 Agent 的工具权限不串线
- 子代理工具调用带正确 executionId/callId
- Plan 不能通过详情页执行 Build 工具

## 非目标

- 本阶段不实现 Agent 权限系统
- 本阶段不实现普通 child chat 的可编辑会话
- 本阶段不允许插件替换宿主只读详情布局
- 本阶段不把子代理内容写进父消息纯文本
- 本阶段不为 UI 卡片增加发送或重试能力

## 文档状态

本文档描述完成，代码实现尚未开始。

[DONE: design documentation]
