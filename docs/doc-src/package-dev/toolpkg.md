# API 文档：`toolpkg.d.ts`

`toolpkg.d.ts` 描述的是工具包插件注册系统。它的核心目标不是“调用工具”，而是**向宿主注册模块、钩子和插件**，让一个 tool package 可以在应用生命周期、消息处理、XML 渲染、输入菜单和提示词流水线中插入自己的行为。

## 作用

当前类型定义覆盖：

- 工具箱 UI 模块注册。
- 应用生命周期钩子。
- 消息处理插件。
- XML 渲染插件。
- 输入菜单开关插件。
- AI 聊天输入框监听和提交 Hook。
- 聊天输入区 UI 插槽渲染插件。
- 聊天消息持久化通知 Hook。
- 工具执行生命周期钩子。
- Prompt 输入、历史、系统提示词、工具提示词、最终发送前的各类钩子。
- 摘要生成阶段的各类钩子。

## 类型命名空间与运行时对象

`toolpkg.d.ts` 里同时存在两个层面的 `ToolPkg`：

- `namespace ToolPkg`：承载类型定义。
- `const ToolPkg: ToolPkg.Registry`：全局运行时注册对象。

因此脚本里常见的实际写法是：

```ts
ToolPkg.registerAppLifecycleHook(...)
ToolPkg.registerMessageProcessingPlugin(...)
```

## 前置 Hook 超时门禁

宿主在“显示与行为”中提供“前置插件 Hook 超时”设置，取值范围为 1 至 60 秒，默认值为 10 秒。

该限制覆盖同步执行的聊天输入、Prompt 与摘要生成 Hook。一次 bridge 分发内的多个 Hook 共用总等待时间，不会为每个 Hook 重新计算完整超时。

达到截止时间时，宿主会中断当前 QuickJS 执行并忽略该 Hook 的返回值，随后使用此前已累积的上下文继续消息处理。开发者应确保 Hook 能尽快完成，并且不应依赖超时后的副作用。

`message_processing` 的回复接管链保持独立，不使用该设置。

用户通过聊天输入框提交消息时，聊天输入 Hook 的超时会显示一条聊天内提示，说明该 Hook 已被跳过且消息会继续发送。消息注入使用的 Prompt 输入 Hook 超时会复用 AI 请求重试的非致命错误事件，并由聊天/悬浮窗 Toast 显示；其他 Prompt 与摘要 Hook 超时仍仅写入日志。

此外，全局还声明了一组辅助函数：

- `registerToolPkgToolboxUiModule(...)`
- `registerToolPkgAppLifecycleHook(...)`
- `registerToolPkgMessageProcessingPlugin(...)`
- `registerToolPkgXmlRenderPlugin(...)`
- `registerToolPkgInputMenuTogglePlugin(...)`
- `registerToolPkgChatInputHook(...)`
- `registerToolPkgInputSlotPlugin(...)`
- `registerToolPkgChatMessageHook(...)`
- `registerToolPkgToolLifecycleHook(...)`
- `registerToolPkgPromptInputHook(...)`
- `registerToolPkgPromptHistoryHook(...)`
- `registerToolPkgPromptEstimateHistoryHook(...)`
- `registerToolPkgSystemPromptComposeHook(...)`
- `registerToolPkgToolPromptComposeHook(...)`
- `registerToolPkgPromptFinalizeHook(...)`
- `registerToolPkgPromptEstimateFinalizeHook(...)`
- `registerToolPkgSummaryGenerateHook(...)`

## 基础类型

### `ToolPkg.LocalizedText`

```ts
type LocalizedText = string | { [lang: string]: string }
```

适合标题、描述等多语言文本。

### `ToolPkg.JsonPrimitive` / `ToolPkg.JsonValue` / `ToolPkg.JsonObject`

这一组类型用于约束所有插件返回值和事件载荷的 JSON 结构。

## 事件分类

### 应用生命周期事件：`AppLifecycleEvent`

支持：

- `application_on_create`
- `application_on_foreground`
- `application_on_background`
- `application_on_low_memory`
- `application_on_trim_memory`
- `application_on_terminate`
- `activity_on_create`
- `activity_on_start`
- `activity_on_resume`
- `activity_on_pause`
- `activity_on_stop`
- `activity_on_destroy`

### 通用事件名：`HookEventName`

这是全部 hook 事件的联合类型，除生命周期外还包括：

- `message_processing`
- `xml_render`
- `input_menu_toggle`
- 聊天输入框事件
- 聊天输入区插槽渲染事件
- 聊天消息持久化事件
- 工具生命周期事件
- Prompt 输入 / 历史 / 系统提示词 / 工具提示词 / 最终发送事件
- 摘要生成事件

### Prompt 轮次类型：`PromptTurnKind` / `PromptTurn`

Prompt 相关 hook 和 `message_processing` 插件里的历史消息，统一使用结构化的 `PromptTurn`：

```ts
type PromptTurnKind =
  | 'SYSTEM'
  | 'USER'
  | 'ASSISTANT'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
  | 'SUMMARY'

interface PromptTurn {
  kind: PromptTurnKind
  content: string
  toolName?: string
  metadata?: JsonObject
}
```

注意：

- 这里不再使用旧的 `{ role, content }` 结构。
- `message_processing` 插件收到的 `chatHistory` 也是 `PromptTurn[]`。
- 如果你要复用旧的 role 语义，需要自己把 `kind` 映射成对应角色。

### 工具生命周期事件：`ToolLifecycleEventName`

- `tool_call_requested`
- `tool_permission_checked`
- `tool_execution_started`
- `tool_execution_result`
- `tool_execution_error`
- `tool_execution_finished`

### Prompt 流水线事件

#### `PromptInputEventName`

- `before_process`
- `after_process`

#### `PromptHistoryEventName`

- `before_prepare_history`
- `after_prepare_history`

#### `SystemPromptComposeEventName`

- `before_compose_system_prompt`
- `compose_system_prompt_sections`
- `after_compose_system_prompt`

#### `ToolPromptComposeEventName`

- `before_compose_tool_prompt`
- `filter_tool_prompt_items`
- `after_compose_tool_prompt`

#### `PromptFinalizeEventName`

- `before_finalize_prompt`
- `before_send_to_model`

#### `SummaryGenerateEventName`

- `before_prepare_summary_prompt`
- `before_send_to_model`
- `after_generate_summary`

## 事件对象

所有 hook 事件都继承自：

### `HookEventBase<TEventName, TPayload>`

公共字段包括：

- `event`
- `eventName`
- `eventPayload`
- `toolPkgId?`
- `containerPackageName?`
- `functionName?`
- `pluginId?`
- `hookId?`
- `timestampMs?`

## 各类 payload

### `MessageProcessingEventPayload`

字段包括：

- `messageContent?`
- `chatHistory?: PromptTurn[]`
- `workspacePath?`
- `maxTokens?`
- `tokenUsageThreshold?`
- `probeOnly?`
- `executionId?`

### `XmlRenderEventPayload`

字段包括：

- `xmlContent?`
- `tagName?`

### `InputMenuToggleEventPayload`

字段包括：

- `action?: 'create' | 'toggle' | string`
- `toggleId?`

### `ChatInputEventPayload`

字段包括：

- `chatId?`
- `text?`
- `selectionStart?`
- `selectionEnd?`
- `hasAttachments?`
- `attachmentCount?`
- `isProcessing?`
- `inputStyle?`
- `source?`
- `submitSource?`

聊天输入框事件名包括：

- `input_changed`
- `submit_requested`
- `submitted`

### `InputSlotEventPayload`

聊天输入区插槽是宿主在聊天输入界面内提供的命名 UI 区域。当前支持三个位置：

- `above_input`：输入区容器顶部，输入控件之前
- `input_drawer`：输入控件所在容器内部，输入控件之前
- `input_toolbar_right`：模型选择器右侧的输入工具栏区域

事件名固定为 `render`，payload 字段包括：

- `slot?`
- `chatId?`
- `runtime?`
- `inputStyle?`：`classic` 或 `agent`
- `isProcessing?`
- `isInputFocused?`
- `inputText?`

### `ChatMessageEventPayload`

字段包括：

- `chatId`
- `timestamp`
- `sender`
- `roleName`
- `content`
- `completedAt`
- `provider`
- `modelName`
- `inputTokens`
- `outputTokens`
- `cachedInputTokens`
- `sentAt`
- `outputDurationMs`
- `waitDurationMs`
- `displayMode`
- `selectedVariantIndex`
- `isFavorite`

聊天消息事件名包括：

- `message_persisted`

说明：

- `timestamp` 对应 `ChatMessage.timestamp` / `MessageEntity.timestamp`，也是当前工程用于定位消息的稳定字段。
- `timestampMs` 是 hook 外层事件的派发时间，和 `eventPayload.timestamp` 不是同一个含义。
- `sender` 对应 `ChatMessage.sender` / `MessageEntity.sender`，表示落库消息来源，例如 `user`、`ai`、`summary`。
- `roleName` 对应 `ChatMessage.roleName` / `MessageEntity.roleName`，表示消息展示或角色卡名称，不表示消息来源。
- `message_persisted` 是通知事件，返回值不会改变已持久化的消息。

### `ToolLifecycleEventPayload`

字段包括：

- `toolName`
- `parameters?`
- `description?`
- `granted?`
- `reason?`
- `success?`
- `errorMessage?`
- `resultText?`
- `resultJson?`

### `PromptHookEventPayload`

字段包括：

- `stage?`
- `functionType?`
- `promptFunctionType?`
- `useEnglish?`
- `rawInput?`
- `processedInput?`
- `chatHistory?: PromptTurn[]`
- `preparedHistory?: PromptTurn[]`
- `systemPrompt?`
- `toolPrompt?`
- `modelParameters?`
- `availableTools?`
- `metadata?`

### `SummaryGenerateEventPayload`

字段包括：

- `stage?`
- `functionType?`
- `useEnglish?`
- `previousSummary?`
- `chatHistory?: PromptTurn[]`
- `preparedHistory?: PromptTurn[]`
- `systemPrompt?`
- `summaryPrompt?`
- `summaryResult?`
- `modelParameters?`
- `metadata?`

## 返回值类型

### 消息处理插件返回：`MessageProcessingHookReturn`

允许返回：

- `boolean`
- `string`
- `MessageProcessingHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `MessageProcessingHookObjectResult` 可包含：

- `matched?`
- `text?`
- `content?`
- `chunks?`

### XML 渲染插件返回：`XmlRenderHookReturn`

允许返回：

- `string`
- `XmlRenderHookObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中 `XmlRenderHookObjectResult` 可包含：

- `handled?`
- `text?`
- `content?`
- `composeDsl?`

`composeDsl` 结构里可以返回：

- `screen: ComposeDslScreen`
- `state?`
- `memo?`
- `moduleSpec?`

### 输入菜单开关返回：`InputMenuToggleHookReturn`

允许返回：

- `InputMenuToggleDefinitionResult[]`
- `InputMenuToggleObjectResult`
- `null`
- `void`
- 或对应的 `Promise`

其中单个开关定义包含：

- `id`
- `title`
- `description?`
- `isChecked?`

### 聊天输入框返回：`ChatInputHookReturn`

`input_changed`、`submitted` 的返回值会被忽略。

`submit_requested` 支持返回：

- `null`
- `void`
- `string`：表示替换本次提交文本
- `{ action: 'allow' }`
- `{ action: 'block', message?: string }`
- `{ action: 'replace', text: string }`
- `{ action: 'consume', message?: string, clearInput?: boolean }`
- 或对应的 `Promise`

### 输入区插槽返回：`InputSlotRenderReturn`

插槽 Hook 返回值支持：

- 非空字符串：以宿主默认文本样式渲染
- `{ text: string }`
- `{ content: string }`
- `{ composeDsl: { screen, state?, memo?, moduleSpec? } }`
- `null` 或 `void`：本次不渲染内容

对象可以包含 `handled: false` 以明确表示不渲染。`composeDsl.screen` 必须是
当前 ToolPkg 归档内的 Compose DSL screen，宿主会复用现有 Compose DSL renderer
和独立的执行上下文。

插槽 Hook 受“前置插件 Hook 超时”设置约束。宿主会为一次插槽分发内的多个
插件共享一个截止时间；Hook 超时或抛出异常时只跳过该插件，原有输入区继续工作。
输入文字变化不会单独触发插槽 Hook 解析，避免每次击键启动完整的插件执行链。

### 聊天消息持久化返回

`message_persisted` 的返回值会被忽略。

### Prompt 相关返回

- `PromptInputHookReturn`
- `PromptHistoryHookReturn`
- `SystemPromptComposeHookReturn`
- `ToolPromptComposeHookReturn`
- `PromptFinalizeHookReturn`
- `SummaryGenerateHookReturn`

这几类返回允许在字符串、消息数组、结构化对象与空返回之间切换，具体以类型定义为准。
其中：

- `PromptHistoryHookReturn` 里的数组元素类型是 `PromptTurn`
- `PromptFinalizeHookReturn` 里的数组元素类型也是 `PromptTurn`
- 估算阶段的 `PromptEstimateHistoryHook` / `PromptEstimateFinalizeHook` 复用相同的 payload 和返回结构
- `SummaryGenerateHookReturn` 可以返回字符串或结构化对象；字符串在摘要生成前阶段会被当作 `summaryPrompt`，在 `after_generate_summary` 阶段会被当作 `summaryResult`

## 注册定义对象

### `ToolboxUiModuleRegistration`

字段：

- `id`
- `runtime?`
- `screen: ComposeDslScreen`
- `params?`
- `title?`

### `AppLifecycleHookRegistration`

字段：

- `id`
- `event`
- `function`

### `MessageProcessingPluginRegistration`

字段：

- `id`
- `function`

### `XmlRenderPluginRegistration`

字段：

- `id`
- `tag`
- `function`

### `InputMenuTogglePluginRegistration`

字段：

- `id`
- `function`

### `ChatInputHookRegistration`

字段：

- `id`
- `function`

### `InputSlotPluginRegistration`

字段：

- `id`
- `slot`：`above_input`、`input_drawer` 或 `input_toolbar_right`
- `function`

### `ChatMessageHookRegistration`

字段：

- `id`
- `function`

### 其余注册对象

以下注册对象结构都很简单，字段都是：`id` + `function`：

- `ToolLifecycleHookRegistration`
- `PromptInputHookRegistration`
- `PromptHistoryHookRegistration`
- `PromptEstimateHistoryHookRegistration`
- `SystemPromptComposeHookRegistration`
- `ToolPromptComposeHookRegistration`
- `PromptFinalizeHookRegistration`
- `PromptEstimateFinalizeHookRegistration`
- `SummaryGenerateHookRegistration`

## `ToolPkg.Registry`

运行时 `ToolPkg` 对象实现了这个接口，提供以下方法：

- `registerToolboxUiModule(definition)`
- `registerUiRoute(definition)`
- `registerNavigationEntry(definition)`
- `registerFloatingWindow(definition)`
- `registerAppLifecycleHook(definition)`
- `registerMessageProcessingPlugin(definition)`
- `registerXmlRenderPlugin(definition)`
- `registerInputMenuTogglePlugin(definition)`
- `registerChatInputHook(definition)`
- `registerInputSlotPlugin(definition)`
- `registerChatMessageHook(definition)`
- `registerToolLifecycleHook(definition)`
- `registerPromptInputHook(definition)`
- `registerPromptHistoryHook(definition)`
- `registerPromptEstimateHistoryHook(definition)`
- `registerSystemPromptComposeHook(definition)`
- `registerToolPromptComposeHook(definition)`
- `registerPromptFinalizeHook(definition)`
- `registerPromptEstimateFinalizeHook(definition)`
- `registerSummaryGenerateHook(definition)`
- `readResource(key, outputFileName?)`
- `host.call(capability, payload)`

### `ToolPkg.readResource(...)`

把当前 toolpkg `manifest.resources` 里声明的资源按 `key` 释放到宿主临时目录，并返回落盘后的绝对路径。

```ts
const jarPath = await ToolPkg.readResource('apktool_lib_jar', 'apktool-lib.jar');
```

说明：

- 这个方法不依赖 `compose_dsl` 的 `ctx`，普通子包工具函数、主入口 hook、UI 模块都可以直接调用。
- `key` 对应 `manifest.json` 里的 `resources[].key`。
- `outputFileName` 可选；不传时会使用清单资源原始文件名。
- 如果资源 `mime` 是目录类型（例如 `inode/directory`、`vnd.android.document/directory`），运行时会先把该目录压成 zip，再返回这个 zip 文件的绝对路径；默认文件名会自动补 `.zip`。
- `registerToolPkg()` 执行期间不可调用；调用会立即抛出异常。

## ToolPkg Logo

ToolPkg 可以把包 Logo 作为普通资源随归档分发。`logo` 填写资源 key，资源
必须是文件，支持 SVG、PNG、JPEG 和 WebP：

```json
{
  "logo": "package_logo",
  "resources": [
    {
      "key": "package_logo",
      "path": "resources/logo.svg",
      "mime": "image/svg+xml"
    }
  ]
}
```

没有 `logo` 字段的旧包继续使用宿主默认图标。宿主从已安装的 ToolPkg
缓存中读取该资源。市场返回的 entry 可以带可选 `logoUrl` 供客户端展示，
但客户端不上传、不托管 Logo，也不会在发布、更新或新版本请求中发送 Logo。

### `ToolPkg.host.call(...)`

`ToolPkg.host.call()` 是由宿主声明 capability 后提供的异步 JSON 桥接。包必须在 `manifest.json` 的 `required_host_capabilities` 中声明 capability，宿主会同时校验包是否启用和当前运行时的包身份。

完整的 capability 清单、请求响应字段、状态语义和悬浮窗开发规范见
[ToolPkg 宿主能力开发规范](./host-capabilities.md)。

桥接只返回 capability 对应的 DTO，不向沙盒传递 API Key、Cookie、Authorization header 或宿主对象。大整数和金额应按字符串处理，返回值中的 `state` 用于区分 `ready`、`credential_required`、`baseline` 和 `error` 等明确状态。

```ts
const snapshot = await ToolPkg.host.call(
  'deepseek.cached_snapshot.v2',
  { configId: 'default', keyId: 'primary' },
);
```

宿主 capability 是版本化接口。包应在界面中展示桥接返回的状态，不应在包内执行凭据读取或直接复刻宿主网络服务。

### `ToolPkg.floatingWindow`

`ToolPkg.registerFloatingWindow()` 注册一个由同一 ToolPkg `compose_dsl` route 承载的系统悬浮窗。注册不会自动显示，只有调用 `show()` 后宿主才会创建 Overlay 服务和该窗口的 Compose/JavaScript runtime。当前浮窗 capability 为 `toolpkg.floating_window.v4`。

固定视口、跟随窗口、媒体反馈、刷新函数、持久化和并发语义见
[ToolPkg 宿主能力开发规范](./host-capabilities.md) 的固定视口悬浮窗章节。

```ts
ToolPkg.registerFloatingWindow({
  id: "whale",
  contentRoute: "toolpkg:com.example.demo:ui:overlay",
  widthDp: 320,
  heightDp: 420,
  draggable: true,
  resizable: true,
  snapMode: "quarter",
  contentLayout: {
    mode: "fixed",
    widthDp: 320,
    heightDp: 420,
    scaleMode: "fit",
  },
  follow: {
    windowId: "anchor_window",
    placement: "above",
    offsetDp: { x: 0, y: 0 },
  },
  pressFeedback: {
    soundResource: "press_sound",
    animation: {
      scaleX: 1.05,
      scaleY: 0.9,
      durationMs: 90,
      easing: "overshoot",
      pivotX: 0.5,
      pivotY: 1,
    },
  },
  releaseFeedback: {
    soundResource: "release_sound",
    animation: {
      scaleX: 1,
      scaleY: 1,
      durationMs: 220,
      easing: "overshoot",
      pivotX: 0.5,
      pivotY: 1,
    },
  },
  refreshIntervalMs: 60000,
  onRefresh: refreshOverlay,
});

await ToolPkg.floatingWindow.show("whale", {});
const state = await ToolPkg.floatingWindow.get("whale");
await ToolPkg.floatingWindow.update("whale", {
  widthDp: 240,
  heightDp: 240,
  alpha: 0.85,
  snapMode: "none"
});
await ToolPkg.floatingWindow.update("whale", { routeArgs: {} });
await ToolPkg.floatingWindow.hide("whale");
```

每个窗口 ID 是单实例。`hide()` 会取消宿主刷新任务并释放窗口运行时；插件停用时宿主也会立即清理所有窗口。用户显式显示的窗口位置和显示状态会在进程被系统回收后恢复，显式隐藏和插件停用会清除恢复标记。应用强制停止会终止当前服务，插件不应依赖强制停止后的恢复时机。

## AssemblyScript WASM 模块

企业插件可以在 `manifest.json` 中声明 AssemblyScript 编译得到的 `.wasm` 核心模块：

```json
{
  "wasm_modules": [
    {
      "id": "core",
      "path": "modules/core.wasm",
      "exports": ["isPrime", "nthPrime"],
      "source_language": "assemblyscript",
      "abi": "assemblyscript"
    }
  ]
}
```

建议结构：

```text
my_toolpkg/
├── manifest.json
├── package.json
├── src/
│   ├── main.ts
│   └── wasm/
│       ├── core.ts
│       └── core.as.ts
├── build/
│   └── main.js
└── modules/
    └── core.wasm
```

AssemblyScript 核心模块示例 `src/wasm/core.as.ts`：

```ts
export function isPrime(n: i32): i32 {
  if (n < 2) return 0;
  for (let divisor: i32 = 2; divisor <= n / divisor; divisor += 1) {
    if (n % divisor === 0) return 0;
  }
  return 1;
}
```

编译示例：

```bash
npx asc src/wasm/core.as.ts --outFile modules/core.wasm --optimize
```

当前宿主会解析和校验 `wasm_modules`。插件的对外入口仍然是 JS `exports` 和 `ToolPkg.register...` 系列 API；作者入口建议写 `src/main.ts`，构建时生成宿主执行用的 `main.js`。`ToolPkg.wasm.call(...)` 不可在 `registerToolPkg()` 执行期间调用，调用会立即抛出异常。

TS facade 示例 `src/wasm/core.ts`：

```ts
export async function isPrime(n: number): Promise<boolean> {
  const result = await ToolPkg.wasm.call("core", "isPrime", [{ type: "i32", value: n }]);
  if (typeof result !== "number") {
    throw new Error("core.isPrime returned a non-number result");
  }
  return result === 1;
}
```

主入口示例 `src/main.ts`：

```ts
import { isPrime } from "./wasm/core";

export async function run(params: { n: number }) {
  return { is_prime: await isPrime(params.n) };
}
```

当前 ABI 支持 `i32`、`i64`、`f32`、`f64`。`i64` 结果以字符串返回；传入 `i64` 时推荐使用字符串，避免 JS number 精度损失。

## 示例

### 注册工具箱 UI 模块

```ts
import toolboxUI from './index.ui.js';

ToolPkg.registerToolboxUiModule({
  id: 'demo_toolbox',
  runtime: 'compose_dsl',
  screen: toolboxUI,
  params: {},
  title: {
    zh: '示例模块',
    en: 'Demo Module'
  }
});
```

### 注册悬浮窗

```ts
ToolPkg.registerFloatingWindow({
  id: 'demo_window',
  contentRoute: 'toolpkg:com.example.demo:ui:dashboard',
  title: {
    zh: '示例浮窗',
    en: 'Demo Window'
  },
  description: {
    zh: '用于长期驻留的示例浮窗',
    en: 'A persistent overlay example'
  },
  widthDp: 320,
  heightDp: 420,
  draggable: true,
  resizable: true,
  contentLayout: {
    mode: 'fixed',
    widthDp: 320,
    heightDp: 420,
    scaleMode: 'fit'
  }
});

await ToolPkg.floatingWindow.show('demo_window');
```

说明：

- `contentRoute` 必须指向同一 ToolPkg 已注册的 `compose_dsl` UI route。
- `show()`、`hide()`、`get()` 和 `update()` 控制单实例浮窗的生命周期和运行配置。
- `snapMode` 支持 `quarter`（屏幕四分之一边缘吸附）和 `none`（自由定位）。
- `contentLayout` 是必填的固定设计视口。宿主按照最终窗口尺寸做一次 `fit` 缩放，并在该视口内统一设计密度和字体比例。
- `follow` 将窗口锚定到同一 ToolPkg 的另一个浮窗，并按 `placement` 和 `offsetDp` 计算位置。支持 `above`、`below`、`start`、`end` 和 `center`。
- `pressFeedback` 和 `releaseFeedback` 分别声明按下、松开时的资源 key 与动画。宿主会在窗口显示时物化资源文件并异步准备媒体流音频。
- 动画支持 `scaleX`、`scaleY`、`alpha`、`translationXDp`、`translationYDp`、`durationMs`、`pivotX`、`pivotY` 和 `linear`、`accelerate`、`decelerate`、`accelerateDecelerate`、`overshoot` 缓动。
- `get()` / `update()` 状态包含尺寸、透明度、位置、吸附模式和反馈配置。
- 浮窗隐藏或插件停用后，宿主会释放该窗口的 Compose 和 JavaScript runtime。

### 注册应用生命周期钩子

```ts
ToolPkg.registerAppLifecycleHook({
  id: 'demo_app_create',
  event: 'application_on_create',
  function(event) {
    console.log(JSON.stringify(event.eventPayload ?? {}));
    return { ok: true };
  }
});
```

### 注册消息处理插件

```ts
ToolPkg.registerMessageProcessingPlugin({
  id: 'demo_message_plugin',
  async function(event) {
    const message = String(event.eventPayload?.messageContent ?? '').trim();
    if (!message.startsWith('/demo')) {
      return { matched: false };
    }
    return {
      matched: true,
      text: '已命中 demo 插件'
    };
  }
});
```

### 注册 XML 渲染插件

```ts
ToolPkg.registerXmlRenderPlugin({
  id: 'demo_xml',
  tag: 'demo',
  function(event) {
    const xml = String(event.eventPayload?.xmlContent ?? '');
    if (!xml) {
      return { handled: false };
    }
    return {
      handled: true,
      text: 'XML 已处理'
    };
  }
});
```

### 注册输入菜单开关插件

```ts
ToolPkg.registerInputMenuTogglePlugin({
  id: 'demo_toggle',
  function(event) {
    if (event.eventPayload?.action === 'create') {
      return [
        {
          id: 'demo_feature',
          title: 'Demo Feature',
          description: '示例开关',
          isChecked: true
        }
      ];
    }
    return [];
  }
});
```

### 注册聊天输入框 Hook

```ts
ToolPkg.registerChatInputHook({
  id: 'demo_chat_input',
  function(event) {
    if (event.eventName === 'input_changed') {
      console.log('draft:', event.eventPayload.text);
      return;
    }

    if (event.eventName === 'submit_requested') {
      const text = event.eventPayload.text || '';
      if (text.includes('/blocked')) {
        return {
          action: 'block',
          message: '这条消息被插件阻止发送'
        };
      }
      if (text.startsWith('/upper ')) {
        return {
          action: 'replace',
          text: text.slice('/upper '.length).toUpperCase()
        };
      }
    }
  }
});
```

### 注册聊天输入区 UI 插槽

`registerInputSlotPlugin()` 注册一个输入区插槽 renderer。一个 ToolPkg 可以为
不同 slot 注册多个 renderer；同一 slot 的多个 ToolPkg 内容会按宿主顺序依次渲染。

```ts
export function renderInputSlot(event: ToolPkg.InputSlotHookEvent) {
  const payload = event.eventPayload;
  if (payload.slot !== 'above_input') {
    return null;
  }

  return {
    handled: true,
    composeDsl: {
      screen: 'dist/ui/input_slot/index.ui.js',
      state: {
        chatId: payload.chatId,
        inputStyle: payload.inputStyle,
        isProcessing: payload.isProcessing
      }
    }
  };
}

export function registerToolPkg() {
  ToolPkg.registerInputSlotPlugin({
    id: 'status_above_input',
    slot: 'above_input',
    function: renderInputSlot
  });
}
```

如果只需要宿主默认文本样式，可以直接返回字符串或 `{ text: '...' }`。插槽
screen 运行在独立的 Compose DSL UI runtime 中，`state`、`memo` 和 `moduleSpec`
会作为该 screen 的初始运行参数传入。插槽不需要声明
`required_host_capabilities`。

`inputText` 会随事件 payload 提供，但输入框每次击键不会单独触发插槽 Hook 重新
解析。需要响应输入内容时，应结合 `isProcessing`、焦点或其他宿主状态设计更新
时机，避免在输入过程中执行昂贵任务。

### 注册聊天消息持久化 Hook

```ts
ToolPkg.registerChatMessageHook({
  id: 'demo_chat_message_sync',
  function(event) {
    if (event.eventName !== 'message_persisted') {
      return;
    }

    const message = event.eventPayload;
    const key = `${message.chatId}:${message.timestamp}`;
    console.log('persisted message:', {
      key,
      sender: message.sender,
      roleName: message.roleName,
      completedAt: message.completedAt,
      length: message.content.length
    });
  }
});
```

### 注册摘要生成 Hook

```ts
ToolPkg.registerSummaryGenerateHook({
  id: 'demo_summary_hook',
  function(event) {
    if (event.eventName === 'before_prepare_summary_prompt') {
      return {
        summaryPrompt: '请重点总结最近的工程决策、已完成事项和下一步待办。'
      };
    }

    if (event.eventName === 'after_generate_summary') {
      return {
        summaryResult: String(event.eventPayload?.summaryResult ?? '').trim()
      };
    }

    return null;
  }
});
```

## 关于 `registerToolPkg()` 入口

从 `examples/linux_ssh/src/main.ts` 与 `examples/deepsearching/src/plugin/deep-search-plugin.ts` 可以看出，工具包通常会在入口文件中导出一个 `registerToolPkg()` 函数，并在里面集中调用上述注册方法。

这是一种**从仓库示例总结出的约定**；它不是 `toolpkg.d.ts` 本身直接声明的函数签名。

包管理器会在独立的临时 QuickJS 引擎中执行每个工具包的 `registerToolPkg()`，单包最长执行 12 秒。该入口只应用于声明注册项，不应启动常驻定时器、无限循环或等待长期任务。`ToolPkg.readResource(...)` 与 `ToolPkg.wasm.call(...)` 在此阶段会立即抛出异常。注册结束后临时引擎会被销毁，工具调用与 UI hook 在各自的运行时 context 中执行，因此不要依赖注册阶段留下的 JavaScript 全局状态。

## 开发调试安装

`toolpkg.d.ts` 这里描述的是注册 API，本身不负责“如何调试安装到手机”。

如果你在开发 ToolPkg，需要注意：

- 普通 `.js` 包可以用 `tools/adb/execute_js.bat` / `tools/adb/execute_js.sh` 做单次执行调试
- `toolpkg` 不适合这样调试，因为它涉及 `manifest`、`main` 注册、ToolPkg cache、以及多类 hook/runtime 的重新同步
- 调试 ToolPkg 时，应使用 `tools/toolpkg/debug_toolpkg.bat` / `tools/toolpkg/debug_toolpkg.sh` / `tools/toolpkg/debug_toolpkg.py`

完整的打包、烧录、启用、刷新 hook/runtime 的工作流说明，见 [TOOLPKG_FORMAT_GUIDE.md](../../TOOLPKG_FORMAT_GUIDE.md) 中的“10.3 使用调试安装脚本快速烧录到手机”。

## 相关文件

- `examples/types/toolpkg.d.ts`
- `examples/types/compose-dsl.d.ts`
- `docs/doc-src/package-dev/core.md`
- `docs/TOOLPKG_FORMAT_GUIDE.md`
