---
title: DeepSeek Whale Overlay ToolPkg
fork: https://github.com/tuxKOH/Operit
status: in_progress
---

# DeepSeek Whale Overlay ToolPkg

## 当前状况

`MeteorNOX/DeepSeek-Balance-Whale-Widget` 是面向 DSH Web 宿主的鲸鱼余额挂件。它直接持有 DeepSeek API Key 和平台 Token，并通过宿主事件刷新余额、平台用量和最近一轮统计。

Operit 当前已经支持 ToolPkg 页面和消息 Hook，但没有通用的长期驻留插件悬浮窗能力，也没有向沙盒暴露 DeepSeek 配置、凭据、余额请求和统计账本接口。

## 预期结果

- 重做版本化的 ToolPkg 浮窗宿主桥接，清理未正式稳定的旧浮窗契约
- 宿主持有凭据并执行 DeepSeek 网络请求，沙盒只接收能力受限 DTO
- 新增 `dsh-whale-widget` ToolPkg，提供设置页、余额页和小尺寸气泡悬浮窗
- 插件启停状态控制页面和悬浮窗的有效显示状态
- 复用现有 Token usage、价格和配置服务，不改变现有 Room 数据结构

## 步骤

1. [DONE: 宿主桥接契约](./01_HostBridge.md)
2. [DONE: DeepSeek 数据服务](./02_DeepSeekData.md)
3. [DONE: ToolPkg 与悬浮窗](./03_ToolPkg.md)
4. [IN_PROGRESS: 验证与交付](./04_Verification.md)
5. [DONE: 触控与双浮窗改造](./05_Interaction.md)
6. [DONE: 通用浮窗反馈与跟随机制](./06_FloatingWindowV3.md)
7. [IN_PROGRESS: 固定视口与媒体反馈重做](./07_FloatingWindowV4.md)
8. [IN_PROGRESS: 连续点按与设置页密度优化](./08_InteractionAndSidebar.md)

## 执行约束

- 当前 UI 方案仍在开发阶段，本轮允许删除旧的单窗口浮窗方案
- 不向 ToolPkg 传递 API Key、Cookie、Authorization header 或宿主对象
- 不在悬浮窗 Compose renderer 中执行网络请求
- 本次执行不触发构建、编译或测试命令

## 完成记录

- 新增通用 `ToolPkg.host.call()`、`ToolPkg.registerFloatingWindow()`、`ToolPkg.floatingWindow` 和 `required_host_capabilities`
- 新增 DeepSeek 余额、平台用量、Token 统计、加密平台凭据和余额快照服务
- 新增独立 `dsh-whale-widget` 包及 Whale PNG 资源
- 插件禁用后，已显示的悬浮窗立即移除，不再执行已禁用包的 renderer；显式显示状态支持进程回收恢复
- 悬浮窗改为独立鲸鱼窗口和跟随气泡窗口，拖动中跟手，气泡拥有独立触控区域
- 删除了上一轮临时 DesktopWidget 注册、Glance 宿主、配置 Activity 和示例
- 独立公开仓库：[luojiaping/operit-deepseek-whale-widget](https://github.com/luojiaping/operit-deepseek-whale-widget)
- 上一版开发包为 `v0.2.0`，删除了旧的单窗口气泡布局
- 宿主 Release 构建提交为 `75004e544`，远程构建通过
- 独立插件发布 [`v0.2.0`](https://github.com/luojiaping/operit-deepseek-whale-widget/releases/tag/v0.2.0)，包含独立鲸鱼/气泡窗口和跟随触控修复
- 本轮新增通用浮窗 `get()`、`snapMode`、透明度、声音反馈和 `Slider` 设置控件，并完成双浮窗交互改造
- `jq`、`file`、`git diff --check` 和 `.toolpkg` ZIP 校验已通过；环境没有 `node`，未执行 JavaScript 语法检查
- 已将旧浮窗能力重做为通用 `toolpkg.floating_window.v3`，该测试接口现已废弃
- `v0.3.0` 测试包的音频和缩放实现存在缺陷，已删除误发布的 GitHub Release 与 tag
- 当前测试迭代重做为 `toolpkg.floating_window.v4`；旧 v3 配置、持久化和插件接口不保留
- 当前测试包版本为 `0.4.0-test.2`，宿主仓库不重复存放鲸鱼插件资源和 UI
- 宿主提交 `1ca6480b8` 已通过远程 Release 编译；插件提交 `710e4c5` 对应本地测试归档
- 独立插件 `v0.4.0-test.2` 待上传测试 ToolPkg
