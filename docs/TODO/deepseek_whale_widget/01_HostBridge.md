---
title: 宿主桥接契约
status: complete
---

# 宿主桥接契约

## 旧实现

`ToolPkg.ipc` 只在 ToolPkg 的 main、UI、provider 等 JavaScript runtime 之间路由，不能直接访问 Kotlin 宿主服务。

## 修改意图

增加带包身份和 capability 校验的异步宿主桥接。桥接使用 `schemaVersion`，所有计数和金额以字符串传输，错误和初始余额基线使用明确状态表示。

## 计划接口

- `deepseek.balance.v2`
- `deepseek.platform_usage.v2`
- `deepseek.stats.v2`
- `toolpkg.floating_window.v4`

桥接返回配置摘要、凭据状态、余额、平台用量、Token usage、价格快照和最近回合，不返回原始凭据。

## 完成记录

`JsEngine`、运行时脚本、ToolPkg 类型声明和 manifest parser 已支持版本化宿主桥接。调用时校验当前执行 session 的包身份、启用状态和 capability 声明。
