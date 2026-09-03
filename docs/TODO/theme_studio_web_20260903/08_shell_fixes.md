# 08 预览壳修复

## 现状

设备框/壳存在布局与交互缺陷（盘点编号 M2–M7、C2）：`flex min-width` 剪裁、缩放除数偏差、双滚动条、`min-height:100vh` 冲突、iframe 根绝对路径、`preview-state` 全量高频 postMessage、slotRunner 类型违规（`runtime:'MAIN'`、any、不校验 slot、单注册丢弃、无 trim）等。

## 意图

在主题预览通道重做时同步修复壳层，使 studio 在窄窗口/移动端/大字号下可用；插件工坊 tab 保留但其契约修复（导出函数等）作为可选项处理。

## 期待

- 布局：`.device-stage` 加 `min-width:0`，缩放逻辑按设备实际 862×404 + 12px padding 对齐（修 M5）；`100dvh` 优先（删 `min-height:100vh` 冲突）；单一纵向滚动（studio-side/preview-panel 去双 overflow）；iframe 地址基于 `import.meta.env.BASE_URL` 相对化。
- 桥/状态：`event.source` 双侧校验、payload 字段校验、mode 过滤 flush、`preview-state` debounce（已在 06 项内，壳层不再重复）。
- slotRunner 修复（工坊相关）：`runtime:'main'`、slot 白名单（above_input/input_drawer/input_toolbar_right）、注册结果数组语义（多注册不丢弃）、trim 空值→null、render 非字符串返回值安全处理；`templates.ts` 生成物改为**导出的命名函数** + `manifest.main` 必填校验（C1）；类型严格化（去 `any`/`as Error`/`String(??)`）。
- tsconfig：`include` 纳入 tests；`typecheck`/`test` 脚本维持并在 CI 生效（见 09）。

## 最小功能单元

[IN PROGRESS] 1. 设备框/壳布局修复：背景层 fixed→absolute 已修；min-width、缩放除数、dvh、单滚动容器、BASE_URL 相对化未完。

[TODO] 2. slotRunner/templates/toolpkgLoader 契约修复（C1/M1/M2/M6/M7 收敛 + 用例）。

[IN PROGRESS] 3. tsconfig 纳入 tests 并保持 `npm run typecheck` 通过。

## 旧实现

见盘点编号；本文件修复目标即盘点清单。

## 进展

[DONE] 聊天背景层从 `position: fixed` 改为受 `.ai-chat-screen` 约束的 `position: absolute`，避免覆盖 iframe 内 AppShell 顶栏；已通过本地 headless 截图确认顶栏、聊天 header、消息区和 composer 正常绘制。

[IN PROGRESS] 预览壳增加聊天目标选择高亮，右侧 Inspector 可按目标切换参数；仍需完成窄屏单滚动容器和最终移动端截图验收。
