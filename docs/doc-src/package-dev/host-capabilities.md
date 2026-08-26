---
title: ToolPkg 宿主能力开发规范
status: current
---

# ToolPkg 宿主能力开发规范

本文面向 ToolPkg 开发者，描述当前 Operit 宿主向 ToolPkg 提供的版本化扩展
能力。文档中的 capability 名称、字段名、状态值和数值单位均为接口契约的一部分。
插件不应通过探测内部实现来推断能力行为，应在 `manifest.json` 中声明所需能力，
并按本文档处理成功、缺少配置和错误状态。

当前宿主已注册的能力分为两组：

| 类型 | capability | 用途 |
|------|------------|------|
| 通用 | `toolpkg.floating_window.v4` | 注册、显示、隐藏、更新和读取 ToolPkg 系统悬浮窗 |
| DeepSeek | `deepseek.accounts.v2` | 读取已配置的 DeepSeek 账户和脱敏 Key 列表 |
| DeepSeek | `deepseek.balance.v2` | 读取 DeepSeek 余额并维护余额差额账本 |
| DeepSeek | `deepseek.cached_snapshot.v2` | 读取宿主保存的余额快照 |
| DeepSeek | `deepseek.platform_status.v2` | 查询平台用量 Token 是否已配置 |
| DeepSeek | `deepseek.platform_set.v2` | 将用户输入的平台 Token 交给宿主加密保存 |
| DeepSeek | `deepseek.platform_usage.v2` | 查询平台 Token 用量并计算费用 |
| DeepSeek | `deepseek.stats.v2` | 查询 DeepSeek Token 统计和最近一轮用量 |

DeepSeek 能力是宿主内置的数据服务，接口对其他 ToolPkg 同样可用，但仅适合
DeepSeek 相关插件。`toolpkg.floating_window.v4` 是与具体业务无关的通用能力。

## 1. 最小接入流程

### 1.1 在 manifest 中声明能力

`required_host_capabilities` 是字符串数组。名称必须非空，大小写不敏感的重复项
不允许出现：

```json
{
  "schema_version": 1,
  "toolpkg_id": "com.example.status_overlay",
  "version": "1.0.0",
  "main": "dist/main.js",
  "required_host_capabilities": [
    "toolpkg.floating_window.v4",
    "deepseek.balance.v2"
  ]
}
```

声明能力只表示包需要该能力，不会自动打开悬浮窗，也不会替包申请 Android
悬浮窗权限。包仍需由用户启用，并在需要时主动调用 API。

### 1.2 调用宿主桥接

所有 capability 都通过异步 JSON API 调用：

```ts
const result = await ToolPkg.host.call("deepseek.accounts.v2", {});
```

`ToolPkg.host.call()` 的参数和返回值都是 JSON 对象。调用方应使用
`examples/types/toolpkg.d.ts` 中的 `ToolPkg.HostBridgeApi` 作为通用类型入口，
再为具体 capability 定义本地的请求和响应类型。

### 1.3 调用前置条件

宿主在执行调用前会检查：

- 当前 ToolPkg 已完成导入和初始化
- 当前 ToolPkg 处于启用状态
- 当前运行时身份与调用传入的包身份一致
- capability 已在该包的 `required_host_capabilities` 中声明
- 宿主当前注册了该 capability

任一条件不满足时，Promise 会 rejected。包应捕获异常并在自己的 UI 中显示
可理解的状态，不应把原始堆栈展示给用户。

## 2. 通用桥接协议

### 2.1 宿主内部响应封装

宿主原生桥接使用下列封装格式：

```json
{
  "success": true,
  "schemaVersion": 1,
  "data": {
    "schemaVersion": 2,
    "state": "ready"
  }
}
```

失败时格式为：

```json
{
  "success": false,
  "schemaVersion": 1,
  "code": "invalid_request",
  "message": "configId is required"
}
```

在 ToolPkg JavaScript API 中，成功调用直接 resolve `data`，失败调用会以
`Error` reject。因此插件通常不直接接触 `success`、`code` 和外层
`schemaVersion`，而是读取 capability 数据中的 `schemaVersion`、`state` 或
`status`，并捕获异常：

```ts
async function loadAccounts() {
  try {
    return await ToolPkg.host.call("deepseek.accounts.v2", {});
  } catch (error) {
    console.error("load DeepSeek accounts failed", error);
    throw error;
  }
}
```

当前外层错误码含义如下：

| `code` | 含义 |
|--------|------|
| `invalid_request` | 请求 JSON、必填字段或字段值不符合接口要求 |
| `capability_unavailable` | 包未声明、包未启用、能力未注册或宿主执行能力不可用 |
| `host_error` | 宿主执行过程中发生未分类错误 |

### 2.2 数据约定

- capability 内层 `schemaVersion` 独立于桥接外层版本；升级数据契约时增加
  capability 版本名，例如 `.v2`，不要静默改变旧版本字段含义
- 计数、时间戳和金额在接口中以字符串传输时，JavaScript 应继续按字符串处理
- `null` 表示字段有定义但当前没有值，缺少字段表示该版本没有提供该字段
- `state` 和 `status` 是业务状态，不应被当作 Boolean 使用
- 宿主返回的 DTO 不包含 API key、Cookie、Authorization header 或宿主对象
- 网络请求由宿主执行，插件不应直接复刻宿主凭据读取和网络逻辑

### 2.3 生命周期位置

`registerToolPkg()` 只负责声明 UI route、悬浮窗、Hook 和其他注册项。宿主桥接
调用应放在普通工具函数、Compose DSL screen、action handler 或刷新函数中。
UI runtime、main runtime 和刷新 runtime 之间不共享 JavaScript 全局变量；需要
共享的数据应通过 `ToolPkg.host.call()`、ToolPkg 状态或显式参数传递。

## 3. DeepSeek 能力

### 3.1 共用标识和状态

所有 DeepSeek 能力当前返回内层 `schemaVersion: 2`。账户、余额和统计使用
`configId` 标识模型配置，使用 `keyId` 标识配置中的具体 Key。

单 Key 配置固定使用：

```text
keyId = "primary"
accountId = "<configId>:primary"
```

多 Key 配置使用宿主配置中的 Key ID。`accountId` 是宿主内部账本和缓存的稳定
组合标识，插件只应把它当作 opaque ID 使用。

### 3.2 `deepseek.accounts.v2`

读取当前 Operit 中所有 DeepSeek 模型配置的公开摘要。请求没有必填字段：

```ts
interface AccountsRequest {}
```

成功响应：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "accounts": [
    {
      "configId": "deepseek_default",
      "name": "DeepSeek",
      "modelName": "deepseek-chat,deepseek-reasoner",
      "keys": [
        {
          "id": "primary",
          "name": "Primary",
          "enabled": true,
          "status": "AVAILABLE"
        }
      ]
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
|------|------|------|
| `state` | `"ready"` | 当前版本成功读取配置时为 `ready` |
| `accounts` | array | DeepSeek 配置摘要列表 |
| `configId` | string | 后续余额和统计请求使用的配置 ID |
| `name` | string | 配置显示名称 |
| `modelName` | string | 配置中的模型名称原文，多个模型以逗号分隔 |
| `keys` | array | 脱敏的 Key 摘要列表 |
| `keys[].id` | string | 后续余额请求使用的 Key ID |
| `keys[].name` | string | Key 显示名称 |
| `keys[].enabled` | boolean | 该 Key 是否可用于请求 |
| `keys[].status` | string | 宿主 Key 状态；不要依赖未在本文档列出的枚举值 |

响应不包含任何 Key 原文。单 Key 配置没有已保存 API key 时仍返回 `primary`，
此时 `enabled` 为 `false`、`status` 为 `UNCONFIGURED`。

### 3.3 `deepseek.balance.v2`

使用指定 DeepSeek 配置读取余额，并将本次余额与同一账户、币种和日期的上一
次余额比较，累积到宿主账本。

请求：

```ts
interface BalanceRequest {
  configId: string;
  keyId: string;
  currency?: string;
  timezone?: string;
  date?: string;
  usageMode?: "ledger" | "platform";
}
```

字段约定：

- `configId` 和 `keyId` 必填
- `currency` 默认为 `CNY`，宿主会从 DeepSeek 返回的 `balance_infos` 中精确选择
- `timezone` 使用 Java `ZoneId` 名称，例如 `Asia/Shanghai`
- `date` 使用 `YYYY-MM-DD` 的本地日期；未提供时按 `timezone` 或设备时区计算
- `usageMode` 为 `platform` 时附带平台用量；其他值按余额差额账本模式处理

成功响应示例：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "accountId": "deepseek_default:primary",
  "configId": "deepseek_default",
  "keyId": "primary",
  "currency": "CNY",
  "totalBalance": "12.500000",
  "balances": [
    { "currency": "CNY", "totalBalance": "12.500000" },
    { "currency": "USD", "totalBalance": "0.000000" }
  ],
  "todayUsage": "1.250000",
  "baselineCaptured": false,
  "date": "2026-08-26",
  "updatedAtMs": "1788...",
  "latestTurn": {
    "state": "ready",
    "occurredAtMs": "1788...",
    "configId": "deepseek_default",
    "provider": "DEEPSEEK",
    "model": "deepseek-chat",
    "requestCount": "1",
    "uncachedInputTokens": "100",
    "cachedInputTokens": "20",
    "totalInputTokens": "120",
    "outputTokens": "80",
    "cost": {
      "currency": "CNY",
      "knownAmount": "0.002",
      "unknownContributionCount": "0"
    }
  },
  "usageMode": "ledger",
  "usageState": "disabled"
}
```

响应状态：

| 字段 | 值 | 说明 |
|------|----|------|
| `state` | `baseline` | 当前账户、币种或日期首次建立基线，本次不计为消耗 |
| `state` | `ready` | 已有基线并完成账本计算 |
| `baselineCaptured` | boolean | 本次调用是否刚建立基线 |
| `todayUsage` | string 或 `null` | 账本模式为累计金额字符串；平台模式在平台数据不可用时可能为 `null` |
| `usageMode` | `ledger` 或 `platform` | 实际使用的展示模式 |
| `usageState` | `disabled` | 未请求平台模式 |
| `usageState` | `credential_required` | 请求平台模式但未保存 Platform Token |
| `usageState` | `ready` | 平台用量请求成功 |
| `usageState` | `error` | 平台用量请求失败；余额结果仍可能有效 |
| `latestTurn.state` | `empty` | 没有 DeepSeek 最近一轮记录 |
| `latestTurn.state` | `ready` | 存在最近一轮记录 |

余额下降才会计入账本；余额上升不会产生负消耗。日期、币种或账户变化时，
宿主重新建立基线。余额请求失败、配置不存在、Key 不可用或返回缺少目标币种
时，调用直接 rejected，不会返回伪造的余额结果。

`latestTurn` 中的 token、请求数和时间戳字段均按字符串处理；字段为 `null`
时表示该轮记录没有对应的原始统计值。

### 3.4 `deepseek.cached_snapshot.v2`

读取宿主保存的余额结果，不执行网络请求和账本刷新：

```ts
interface CachedSnapshotRequest {
  configId: string;
  keyId: string;
}
```

存在快照时，响应是最近一次 `deepseek.balance.v2` 结果，包含余额、账本、平台
用量状态和最近一轮信息。没有快照时：

```json
{
  "schemaVersion": 2,
  "state": "empty"
}
```

`configId` 和 `keyId` 都是必填项。快照不包含 API key。

### 3.5 `deepseek.platform_status.v2`

查询宿主加密偏好设置中是否存在 Platform Token。请求为空对象：

```ts
const status = await ToolPkg.host.call("deepseek.platform_status.v2", {});
```

响应：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "configured": true
}
```

没有 Token 时返回 `state: "credential_required"` 和 `configured: false`。
该接口不会返回 Token 原文。

### 3.6 `deepseek.platform_set.v2`

将用户输入的 Platform Token 交给宿主保存：

```ts
interface PlatformSetRequest {
  token: string;
}

const result = await ToolPkg.host.call("deepseek.platform_set.v2", {
  token: userInput,
});
```

`token` 必须是非空字符串。输入可以带一个 `Bearer ` 前缀，宿主保存时会去除
该前缀。宿主使用 Android 加密偏好设置保存 Token，响应只包含：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "configured": true
}
```

插件不得记录 Token、把 Token 写入自己的存储，或把 Token 放入错误信息和 UI
状态文本。用户清除 Token 的接口当前未提供；需要清除时应由宿主设置页面完成。

### 3.7 `deepseek.platform_usage.v2`

使用宿主保存的 Platform Token 查询指定时间范围的平台用量：

```ts
interface PlatformUsageRequest {
  startSeconds: string;
  endSeconds: string;
  timezoneOffsetSeconds: string;
}
```

三个字段都必填，使用 Unix epoch seconds 和秒级时区偏移。推荐传字符串，避免
JavaScript Number 在长时间戳场景下产生精度问题：

```ts
const usage = await ToolPkg.host.call("deepseek.platform_usage.v2", {
  startSeconds: "1788048000",
  endSeconds: "1788134400",
  timezoneOffsetSeconds: "28800",
});
```

成功响应：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "amount": "0.125",
  "tokens": "250000",
  "bucketCount": 24,
  "updatedAtMs": "1788..."
}
```

没有保存 Token 时不执行网络请求，返回：

```json
{
  "schemaVersion": 2,
  "state": "credential_required",
  "configured": false
}
```

`amount` 是按宿主当前 DeepSeek 价格规则计算的 CNY 金额字符串，`tokens` 是
命中、未命中和输出 Token 的总和，`bucketCount` 是纳入计算的时间桶数量。
调用方不应在插件内重复实现价格表或峰谷时间判断。

### 3.8 `deepseek.stats.v2`

查询指定本地日期的 DeepSeek Token 统计：

```ts
interface StatsRequest {
  timezone?: string;
  date?: string;
}
```

响应：

```json
{
  "schemaVersion": 2,
  "state": "ready",
  "date": "2026-08-26",
  "summary": {
    "requests": "12",
    "uncachedInput": {
      "knownSum": "1000",
      "knownEventCount": "12",
      "unknownEventCount": "0",
      "totalEventCount": "12"
    },
    "cachedInput": {
      "knownSum": "200",
      "knownEventCount": "12",
      "unknownEventCount": "0",
      "totalEventCount": "12"
    },
    "totalInput": {
      "knownSum": "1200",
      "knownEventCount": "12",
      "unknownEventCount": "0",
      "totalEventCount": "12"
    },
    "output": {
      "knownSum": "800",
      "knownEventCount": "12",
      "unknownEventCount": "0",
      "totalEventCount": "12"
    },
    "totalTokens": {
      "knownSum": "2000",
      "knownEventCount": "12",
      "unknownEventCount": "0",
      "totalEventCount": "12"
    },
    "cost": {
      "currency": "CNY",
      "knownAmount": "0.05",
      "unknownContributionCount": "0",
      "totalContributionCount": "12"
    }
  },
  "latestTurn": {
    "state": "empty"
  }
}
```

所有 Token 数量、请求数和费用计数均为字符串。`knownSum` 是已知原始值的总和，
`unknownEventCount` 表示该统计项存在但原始值未知的事件数，不能把它当作零值
事件数。`latestTurn` 的 `ready` 结构与余额接口中的结构相同。

## 4. 固定视口悬浮窗

### 4.1 设计模型

悬浮窗由同一个 ToolPkg 内的 `compose_dsl` UI route 承载。注册和运行分为两步：

1. 用 `ToolPkg.registerUiRoute()` 注册内容 route
2. 用 `ToolPkg.registerFloatingWindow()` 注册窗口描述
3. 在工具函数或 UI action 中调用 `ToolPkg.floatingWindow.show()`

`registerFloatingWindow()` 不会自动显示窗口。窗口第一次显示时宿主创建该窗口
自己的 Compose 和 JavaScript runtime；隐藏或停用后宿主释放对应 runtime。

```ts
const OVERLAY_ROUTE = "toolpkg:com.example.status_overlay:ui:overlay";

function OverlayScreen(ctx) {
  const { UI } = ctx;
  return UI.Box({ fillMaxSize: true }, [
    UI.Text({ text: "Status" }),
  ]);
}

function registerToolPkg() {
  ToolPkg.registerUiRoute({
    id: "overlay",
    route: OVERLAY_ROUTE,
    runtime: "compose_dsl",
    screen: OverlayScreen,
    title: { zh: "状态浮窗", en: "Status Overlay" },
  });

  ToolPkg.registerFloatingWindow({
    id: "overlay",
    contentRoute: OVERLAY_ROUTE,
    title: { zh: "状态浮窗", en: "Status Overlay" },
    widthDp: 320,
    heightDp: 180,
    draggable: true,
    resizable: true,
    snapMode: "quarter",
    contentLayout: {
      mode: "fixed",
      widthDp: 320,
      heightDp: 180,
      scaleMode: "fit",
    },
  });
}

exports.registerToolPkg = registerToolPkg;
```

`contentRoute` 必须指向同一 ToolPkg 已注册的 `compose_dsl` route。窗口 ID 在
同一包内必须唯一，大小写不敏感。多个窗口可以共享同一个 UI route，但每个窗口
仍然拥有独立的窗口状态和 runtime 实例。

### 4.2 注册字段

| 字段 | 类型 | 默认值 | 约束和说明 |
|------|------|--------|------------|
| `id` | string | 无 | 必填，包内唯一 |
| `contentRoute` | string | 无 | 必填，同包 `compose_dsl` route ID |
| `title` | `LocalizedText` | `id` | 窗口标题 |
| `description` | `LocalizedText` | 空字符串 | 窗口描述 |
| `icon` | string | 无 | 宿主图标名称 |
| `widthDp` | number | `320` | 初始窗口宽度，`72..1200` |
| `heightDp` | number | `420` | 初始窗口高度，`72..1600` |
| `draggable` | boolean | `true` | 是否允许拖动窗口 |
| `resizable` | boolean | `true` | 是否允许从右下角调整大小 |
| `snapMode` | `"none" \| "quarter"` | `"quarter"` | 松手时自由定位或贴合屏幕边缘 |
| `contentLayout` | object | 无 | 必填，固定设计视口，见下节 |
| `follow` | object | 无 | 跟随同包的另一个可见窗口 |
| `pressFeedback` | object | 空配置 | 按下时的音效和动画 |
| `releaseFeedback` | object | 空配置 | 松开时的音效和动画 |
| `refreshIntervalMs` | number | `60000` | `0` 关闭；否则 `30000..86400000` |
| `onRefresh` | function | 无 | 刷新函数，存在时与周期一起生效 |

设计视口字段：

```ts
interface FloatingWindowContentLayout {
  mode: "fixed";
  widthDp: number;    // 1..1200，设计坐标宽度
  heightDp: number;   // 1..1600，设计坐标高度
  scaleMode: "fit";
}
```

宿主按照实际窗口尺寸和设计视口尺寸计算一次整体 `fit` 比例，并在 Compose
runtime 中提供对应的 density。窗口内容应始终使用设计坐标，不要在
`routeArgs`、字体、偏移、路径或内容框上再次套用窗口缩放比例。

### 4.3 跟随窗口

`follow` 只能引用同一个 ToolPkg 内的另一个窗口：

```ts
interface FloatingWindowFollow {
  windowId: string;
  placement: "above" | "below" | "start" | "end" | "center";
  offsetDp?: { x?: number; y?: number };
}
```

规则如下：

- 被跟随窗口必须先处于可见状态，之后才能显示跟随窗口
- `offsetDp` 使用 dp；`x` 范围为 `-1200..1200`，`y` 范围为 `-1600..1600`
- 宿主在锚点移动或尺寸变化后重新计算跟随窗口位置
- 隐藏锚点时，宿主同时隐藏它的跟随窗口
- 注册阶段会拒绝自引用和跟随环
- 显示恢复时，宿主按锚点到跟随窗口的依赖顺序创建窗口

### 4.4 媒体反馈和动画

音频资源必须先在 `manifest.json` 的 `resources` 中声明，再通过资源 key 引用：

```json
{
  "key": "press_sound",
  "path": "resources/press.mp3",
  "mime": "audio/mpeg"
}
```

```ts
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
}
```

动画字段：

| 字段 | 类型 | 默认值 | 范围或说明 |
|------|------|--------|------------|
| `scaleX` / `scaleY` | number | `1` | `0..4` |
| `alpha` | number | `1` | `0..1`，这是反馈动画透明度 |
| `translationXDp` / `translationYDp` | number | `0` | `-1200..1200`、`-1600..1600` |
| `durationMs` | number | `0` | `0..5000` |
| `easing` | string | `linear` | `linear`、`accelerate`、`decelerate`、`accelerateDecelerate`、`overshoot` |
| `pivotX` / `pivotY` | number | `0.5` | `0..1`，相对于窗口尺寸的比例 |

窗口自身的 `alpha` 通过 `floatingWindow.update()` 设置，范围为 `0.2..1`；
声音总开关 `soundEnabled` 范围是 Boolean，音量 `soundVolume` 范围是 `0..1`。
宿主使用媒体流异步准备音频，反馈资源加载失败会写入宿主日志，不会使窗口
渲染失败。

### 4.5 刷新函数

当 `refreshIntervalMs` 大于零且提供 `onRefresh` 时，宿主在窗口显示后按周期
调用刷新函数。刷新函数运行在 ToolPkg main runtime，调用完成后宿主强制重新
渲染窗口：

```ts
async function refreshOverlay() {
  const data = await ToolPkg.host.call("deepseek.balance.v2", {
    configId: "deepseek_default",
    keyId: "primary",
  });
  return data;
}
```

刷新函数的返回值不会直接替换 UI 树；UI screen 应通过 `ToolPkg.host.call()`
或自己的状态读取数据。刷新函数抛出的异常会记录日志，宿主仍会继续刷新窗口
内容。隐藏窗口、停用包或释放 runtime 时，刷新任务会被取消。

### 4.6 运行时控制 API

```ts
interface FloatingWindowApi {
  show(windowId: string, routeArgs?: JsonObject): Promise<FloatingWindowState>;
  hide(windowId: string): Promise<FloatingWindowState>;
  get(windowId: string): Promise<FloatingWindowState>;
  update(windowId: string, patch?: JsonObject): Promise<FloatingWindowState>;
}
```

示例：

```ts
await ToolPkg.floatingWindow.show("overlay", {
  selectedTab: "summary",
});

const state = await ToolPkg.floatingWindow.get("overlay");

await ToolPkg.floatingWindow.update("overlay", {
  widthDp: 360,
  heightDp: 220,
  alpha: 0.9,
  snapMode: "none",
  soundEnabled: false,
});

await ToolPkg.floatingWindow.hide("overlay");
```

操作语义：

| 操作 | 语义 |
|------|------|
| `show` | 显示或复用窗口；可传入新的 `routeArgs` |
| `hide` | 隐藏窗口并释放该窗口 runtime；同时隐藏它的跟随窗口 |
| `get` | 读取当前运行状态或已持久化的隐藏状态，不启动窗口 |
| `update` | 更新运行状态；窗口隐藏时只更新持久化设置 |

当同一窗口已经可见时，重复 `show()` 只有在 `routeArgs` 发生变化时才会触发
新的内容 render。`routeArgs` 会作为 JSON 字符串放入 Compose DSL module spec：

```ts
type FloatingModuleSpec = {
  routeArgsJson: string;
};

const moduleSpec = ctx.getModuleSpec?.<FloatingModuleSpec>();
if (!moduleSpec) throw new Error("floating window module spec is unavailable");
const routeArgs = JSON.parse(moduleSpec.routeArgsJson);
```

### 4.7 `update()` patch 字段

`patch` 是部分更新对象，未提供的字段保持原值：

| 字段 | 类型 | 说明 |
|------|------|------|
| `routeArgs` | `JsonObject` | 更新 UI route 参数并请求重新渲染 |
| `widthDp` | number | 窗口宽度，宿主限制在 `72..1200` |
| `heightDp` | number | 窗口高度，宿主限制在 `72..1600` |
| `alpha` | number | 窗口透明度，宿主限制在 `0.2..1` |
| `x` / `y` | number | 屏幕像素坐标，不是 dp |
| `snapMode` | `"none" \| "quarter"` | 更新贴边模式 |
| `soundEnabled` | boolean | 开关按下和松开音效 |
| `soundVolume` | number | 音量，宿主限制在 `0..1` |
| `pressFeedback` | object 或 `null` | 部分更新按下反馈；`null` 清空整个反馈 |
| `releaseFeedback` | object 或 `null` | 部分更新松开反馈；`null` 清空整个反馈 |

反馈对象内部同样是部分更新：缺少 `soundResource` 或 `animation` 时保留当前
值，显式传 `null` 时清除对应部分。`routeArgs` 必须是 JSON 对象，不能传数组
或字符串。

### 4.8 状态结构

成功操作返回：

```ts
interface FloatingWindowState {
  schemaVersion: 4;
  windowId: string;
  contentRoute: string;
  status: "visible" | "hidden" | "disabled" | "error";
  widthDp?: number;
  heightDp?: number;
  widthPx?: number;
  heightPx?: number;
  screenWidthPx?: number;
  screenHeightPx?: number;
  draggable?: boolean;
  resizable?: boolean;
  alpha?: number;
  x?: number;
  y?: number;
  snapMode?: "none" | "quarter";
  contentLayout: FloatingWindowContentLayout;
  follow?: FloatingWindowFollow | null;
  soundEnabled?: boolean;
  soundVolume?: number;
  pressFeedback: FloatingWindowFeedback;
  releaseFeedback: FloatingWindowFeedback;
  instanceId?: string;
  updatedAtMs: string;
  errorCode?: string;
  errorMessage?: string;
}
```

当前正常调用主要返回 `visible` 或 `hidden`。调用失败会 rejected；包不应等待
`status: "error"` 继续读取业务字段。`x`、`y`、`widthPx`、`heightPx`、
`screenWidthPx` 和 `screenHeightPx` 都是屏幕像素，窗口的注册尺寸、`update()`
的宽高和跟随偏移使用 dp。插件可以结合 `x + widthPx / 2` 与 `screenWidthPx / 2`
判断窗口当前停靠的屏幕半边。

### 4.9 持久化和生命周期

- 每个包、窗口 ID 组合拥有独立的位置、尺寸、透明度、贴边和声音设置
- 用户主动显示的窗口在宿主进程被系统回收后按保存状态恢复
- 恢复跟随窗口时先恢复锚点，再恢复依赖它的窗口
- 显式隐藏和包停用会清除恢复标记；应用强制停止会终止当前服务，插件不应把强制停止后的恢复时机当作业务数据契约
- 包停用时，宿主立即移除该包全部可见窗口，取消刷新任务，释放 Compose 和
  JavaScript runtime
- 悬浮窗服务使用 Android 前台服务，因此系统会显示对应的低优先级服务通知

### 4.10 点击和 render 并发语义

宿主对每个浮窗实例维护独立的 render 队列：

- 同一 action ID 且没有 payload 的点击 action，同时只执行一个
- 输入框、滑块和选择器等带 payload 的 action 不使用点击 action 的合并规则
- render 请求在同一窗口内合并，执行中的 render 完成后最多继续处理一次待处理
  render
- 同一窗口实例的 `onLoad` 只派发一次；后续 render 不会重复执行 `onLoad`
- 拖拽松手、吸附落位和尺寸调整结束后，宿主会重渲染该窗口并重渲染它的跟随
  窗口，使 UI 内容能及时跟上窗口几何变化
- 宿主不识别业务 action 名称，也不包含任何鲸鱼、气泡或 DeepSeek 专用逻辑

因此插件 action handler 应使用当前 UI 状态判断业务条件，不要通过连续发送同一
个无参数 action 来实现重试。需要携带用户输入的 action 应明确传递 payload。

## 5. Compose DSL 扩展

固定视口悬浮窗使用与普通 ToolPkg UI 相同的 `compose_dsl` renderer。当前宿主
新增或完善的文本、对齐和 Canvas 字段如下。

### 5.1 Text 和 BasicText

`Text`、Material3 生成的 `Text`、`BasicText` 均支持：

```ts
UI.Text({
  text: "Centered",
  fontSize: 16,
  lineHeight: 22,
  textAlign: "center",
  maxLines: 2,
  overflow: "ellipsis",
});
```

`lineHeight` 使用 sp，与 `fontSize` 相同。`textAlign` 的合法值：

```text
start | center | end | left | right | justify
```

`BasicText` 使用相同字段。文本对齐只有在文本拥有可用布局宽度时才会产生可见
差异，固定视口内应通过 `fillMaxWidth`、明确宽度或 Canvas 约束提供布局宽度。

`BasicText` 还支持 `onTextLayout` action。文本完成布局时宿主以 `null` payload
派发该 action；需要携带业务数据时，应在 action handler 中读取当前状态。

`Box`、`BoxWithConstraints`、`Image` 和 Material3 的相关容器支持扩展后的
`contentAlignment` token，包括 `center`、`topStart`、`topCenter`、`topEnd`、
`centerStart`、`centerEnd`、`bottomStart`、`bottomCenter`、`bottomEnd`，以及
对应的 `start`/`end` 别名。

### 5.1.1 水平镜像

`Modifier.scale` 支持单参数均匀缩放和双参数非均匀缩放：

```ts
ctx.Modifier.scale(-1, 1); // 水平镜像翻转，scaleX=-1, scaleY=1
ctx.Modifier.scale(0.8, 1.2);
```

需要水平镜像但文字保持可读时，在翻转后的父容器内给文字容器再套一次
`scale(-1, 1)` 翻回即可。镜像属于绘制和命中变换，不会改变布局位置。

### 5.2 Canvas 数值单位

Canvas 几何值支持数字和带单位对象：

```ts
type ComposeCanvasUnit = "px" | "dp" | "fraction";

type ComposeCanvasNumber = number | {
  value: number;
  unit: ComposeCanvasUnit;
};
```

含义如下：

| 单位 | 含义 |
|------|------|
| `px` | Canvas 实际像素 |
| `dp` | 按当前 Compose density 换算的 dp |
| `fraction` | 相对于 Canvas 宽度或高度的比例，x 轴使用宽度，y 轴使用高度 |

命令可以提供默认 `unit`，也可以让单个数值携带自己的单位：

```ts
const commands: ComposeCanvasCommand[] = [
  {
    type: "line",
    x1: { value: 0.1, unit: "fraction" },
    y1: { value: 0.2, unit: "fraction" },
    x2: { value: 120, unit: "dp" },
    y2: { value: 80, unit: "dp" },
    strokeWidth: { value: 2, unit: "dp" },
  },
];
```

为了避免描边宽度的单位歧义，`strokeWidth` 应使用带 `unit` 的对象。普通数字
描边宽度按 Canvas 像素处理。

### 5.3 Canvas 文本和路径

当前支持的命令包括：

- `line`
- `rect`
- `roundRect`
- `circle`
- `text`
- `drawPath`
- `drawRoundRect`
- `drawText`

`text` 和 `drawText` 支持 `textAlign`、`fontSize`、`minWidth`、`maxWidth`、
`minHeight`、`maxHeight`、`maxLines` 和 `overflow`。`drawPath` 支持
`moveTo`、`lineTo`、`cubicTo`、`quadTo` 和 `close`，并支持 `fill`/`stroke`
样式与带单位的 `strokeWidth`。

当前 renderer 不处理 `drawIcon`，即使部分历史类型声明中存在该名称，也不要将其
作为宿主能力使用。

```ts
UI.Canvas({
  commands: [
    {
      type: "drawText",
      x: { value: 0.5, unit: "fraction" },
      y: { value: 24, unit: "dp" },
      text: "Usage",
      fontSize: 14,
      textAlign: "center",
    },
    {
      type: "drawPath",
      path: [
        { type: "moveTo", x: 0.1, y: 0.8 },
        { type: "lineTo", x: 0.5, y: 0.2 },
        { type: "lineTo", x: 0.9, y: 0.8 },
        { type: "close" },
      ],
      style: "stroke",
      strokeWidth: { value: 1.5, unit: "dp" },
    },
  ],
});
```

## 6. 开发建议

### 6.1 能力声明和版本

- 只声明包实际使用的 capability
- 将 capability 名称视为版本化 API ID，不要拼接未公开的内部名称
- capability 未声明或不可用时，捕获 rejected Promise，并在 UI 中显示可操作
  的状态
- 对 `schemaVersion` 做显式判断；不理解的数据版本不要静默当作旧结构解析

### 6.2 凭据和数据

- 账户接口只用于选择 `configId`、`keyId`，不要期待拿到 Key 原文
- 平台 Token 只通过 `deepseek.platform_set.v2` 提交，不要写入 ToolPkg 存储
- 余额、统计和平台用量的金额及大整数按字符串保存和显示
- 将 `baseline`、`credential_required`、`empty` 和 `error` 显示为不同状态
- 不在 Compose renderer 中直接发起网络请求；将数据加载放在 action、刷新函数
  或宿主 capability 中

### 6.3 悬浮窗

- 固定设计尺寸写入 `contentLayout`，UI 内使用设计坐标
- 先显示锚点窗口，再显示 `follow` 窗口
- `onRefresh` 只负责触发数据更新，不依赖返回值直接更新 UI
- 隐藏窗口时取消自己的刷新和异步任务，避免在已释放 runtime 上继续操作
- 点击 handler 保持幂等；带用户输入的 action 明确传递 payload

## 7. 参考入口

- [ToolPkg API 总文档](./toolpkg.md)
- [ToolPkg 格式说明](../../TOOLPKG_FORMAT_GUIDE.md)
- [ToolPkg 类型声明](../../../examples/types/toolpkg.d.ts)
- [Compose DSL 类型声明](../../../examples/types/compose-dsl.d.ts)
- [DeepSeek 宿主数据实现](../../../app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/DeepSeekWhaleHostService.kt)
- [通用宿主桥接实现](../../../app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgHostBridge.kt)
- [悬浮窗 capability 实现](../../../app/src/main/java/com/ai/assistance/operit/plugins/toolpkg/ToolPkgFloatingWindowHost.kt)
- [悬浮窗服务实现](../../../app/src/main/java/com/ai/assistance/operit/services/floating/ToolPkgFloatingWindowService.kt)
