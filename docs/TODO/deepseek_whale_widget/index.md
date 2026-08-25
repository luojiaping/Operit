---
title: DeepSeek Whale Overlay ToolPkg
fork: https://github.com/tuxKOH/Operit
status: complete
---

# DeepSeek Whale Overlay ToolPkg

## 当前状况

`MeteorNOX/DeepSeek-Balance-Whale-Widget` 是面向 DSH Web 宿主的鲸鱼余额挂件。它直接持有 DeepSeek API Key 和平台 Token，并通过宿主事件刷新余额、平台用量和最近一轮统计。

Operit 当前已经支持 ToolPkg 页面和消息 Hook，但没有通用的长期驻留插件悬浮窗能力，也没有向沙盒暴露 DeepSeek 配置、凭据、余额请求和统计账本接口。

## 预期结果

- 新增版本化的 ToolPkg 宿主桥接，不修改现有 ToolPkg API
- 宿主持有凭据并执行 DeepSeek 网络请求，沙盒只接收能力受限 DTO
- 新增 `dsh-whale-widget` ToolPkg，提供设置页、余额页和小尺寸气泡悬浮窗
- 插件启停状态控制页面和悬浮窗的有效显示状态
- 复用现有 Token usage、价格和配置服务，不改变现有 Room 数据结构

## 步骤

1. [DONE: 宿主桥接契约](./01_HostBridge.md)
2. [DONE: DeepSeek 数据服务](./02_DeepSeekData.md)
3. [DONE: ToolPkg 与悬浮窗](./03_ToolPkg.md)
4. [DONE: 验证与交付](./04_Verification.md)

## 执行约束

- 当前 Operit 版本已经发布，新增接口必须向前兼容
- 不向 ToolPkg 传递 API Key、Cookie、Authorization header 或宿主对象
- 不在悬浮窗 Compose renderer 中执行网络请求
- 本次执行不触发构建、编译或测试命令

## 完成记录

- 新增通用 `ToolPkg.host.call()`、`ToolPkg.registerFloatingWindow()`、`ToolPkg.floatingWindow` 和 `required_host_capabilities`
- 新增 DeepSeek 余额、平台用量、Token 统计、加密平台凭据和余额快照服务
- 新增 `examples/deepseek_whale_widget` 包及 Whale PNG 资源
- 插件禁用后，已显示的悬浮窗立即移除，不再执行已禁用包的 renderer；显式显示状态支持进程回收恢复
- 悬浮窗采用新的方形鲸鱼布局，拖动中跟手，中央位置保持自由定位，边缘区域按释放位置吸附
- 删除了上一轮临时 DesktopWidget 注册、Glance 宿主、配置 Activity 和示例
- 独立公开仓库：[luojiaping/operit-deepseek-whale-widget](https://github.com/luojiaping/operit-deepseek-whale-widget)
- 发布包更新为 [`v0.1.3`](https://github.com/luojiaping/operit-deepseek-whale-widget/releases/tag/v0.1.3)，包含小尺寸气泡悬浮窗
- 宿主 Release 构建提交为 `29f07e491`，远程构建通过
- `jq`、`file`、`git diff --check` 和 `.toolpkg` ZIP 校验已通过；环境没有 `node`，未执行 JavaScript 语法检查
