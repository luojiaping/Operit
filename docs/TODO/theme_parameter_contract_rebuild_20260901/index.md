---
title: 全量主题参数契约与精简设置页
fork: https://github.com/luojiaping/Operit.git
branch: feat/plugin-interface
status: implementation_in_progress
---

# 全量主题参数契约与精简设置页

## 背景

schema 3 仅声明颜色和图片 URI 两种参数，默认主题只公开 accent 与背景图。它没有承接开发基线的可调能力，也把能力边界和设置页内容混为一谈。

本批以 `95619952` 的开发基线为参数能力下限。新契约可以表达全部基线字段，并新增模式独立颜色、组件状态、场景媒体、结构化图片布局和主题作者自定义公开范围。用户设置页只展示主题包标记为用户可见、易于理解且有真实运行时消费者的项目。

## 已确认边界

- schema 3、`2.2.0` archive 和新设置页均未正式发布。
- 不读取、迁移或维护旧主题参数与旧主题实例数据。
- 角色/群组头像、聊天标题等业务元数据不属于全局主题包。
- 消息诊断、身份和活动展示开关不属于主题设置，保留在独立 Display & Behavior 入口。
- 不恢复目标选择、草稿、保存/重置命令栏、重复预览或五标签长表单。

## 目录

1. [字段矩阵与产品边界](./1_FieldMatrixAndProductBoundary.md)
2. [schema 4 参数与资源契约](./2_ParameterContractAndStorage.md)
3. [运行时投影与宿主](./3_RuntimeProjectionAndHosts.md)
4. [设置页与内置主题](./4_SettingsAndBundledThemes.md)
5. [测试与验收](./5_TestingAndAcceptance.md)

## 完成定义

- 参数能力覆盖开发基线，并具备新增的强类型 scene、component 和媒体绑定。
- 用户设置页由 manifest 可见性和分组驱动，不含历史编辑器交互。
- Android、离屏渲染和 WebChat 使用相同的解析结果。
- default 与 Cyber Grid 都声明自己的可调参数和 effect，不跨包改写资源。
- schema 3 的解析、存储清理和 UI 特例无生产引用。

## 进展

[DONE] schema 4 value/control/effect、显式 package behavior、schema-4-only selection store 与资源授权路径已实现。

[DONE] default 与 Cyber Grid source archive 已重打，default `3.0.0` 已内置 APK asset，Cyber basis 精确指向该 artifact。

[PARTIAL] Android、离屏和 WebChat 已接入同一 runtime projection；focused tests 已更新但尚未执行。
