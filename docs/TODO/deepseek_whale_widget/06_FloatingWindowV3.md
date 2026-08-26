---
title: 通用浮窗反馈与跟随机制
status: complete
---

# 通用浮窗反馈与跟随机制

> 此测试期 v3 契约已由 v4 固定设计视口和媒体反馈契约替换，不再支持。

## 旧实现

- 浮窗桥接和反馈字段还没有统一的通用契约
- 跟随关系使用单轴间距，不能表达方向和二维偏移
- 按压和释放音效字段与动画能力没有统一描述
- 按压缩放动效曾存在于宿主实现，后续重构中被删除
- Compose DSL `Text` 没有公开文字对齐属性

## 目标实现

- 使用 `toolpkg.floating_window.v3`
- 用 `follow` 对象描述锚点、方向和二维偏移
- 用 `pressFeedback` 与 `releaseFeedback` 描述资源键和动画
- 宿主按注册声明执行动画、异步预加载音效和通用窗口定位
- Compose DSL 为 `Text`、`BasicText` 和 Canvas 文本提供 `textAlign`
- 宿主核心不识别鲸鱼、气泡、DeepSeek 或任何插件资源名称

## 契约草案

```ts
interface FloatingWindowFollow {
  windowId: string;
  placement: "above" | "below" | "start" | "end" | "center";
  offsetDp?: { x?: number; y?: number };
}

interface FloatingWindowAnimation {
  scaleX?: number;
  scaleY?: number;
  alpha?: number;
  translationXDp?: number;
  translationYDp?: number;
  durationMs?: number;
  easing?: "linear" | "accelerate" | "decelerate" | "accelerateDecelerate" | "overshoot";
  pivotX?: number;
  pivotY?: number;
}

interface FloatingWindowFeedback {
  soundResource?: string | null;
  animation?: FloatingWindowAnimation | null;
}
```

## 完成条件

- v3 注册、parser、runtime model、PackageManager DTO、host bridge、service 和 JS facade 使用同一组字段
- Whale 与 Bubble 只在插件中声明跟随、反馈、路径和文本布局
- 首次显示后立即点击仍能在音频样本加载完成后播放反馈
- `above`、`below`、`start`、`end`、`center` 和二维偏移按声明定位
- 气泡内余额、随机台词和 GIF 内容在其可见图形内对齐
- src、dist 和 ToolPkg manifest 保持一致

## 完成记录

- 宿主 capability 改为 `toolpkg.floating_window.v3`
- 跟随定位改为通用五方向和二维 `offsetDp`
- 反馈改为通用 `pressFeedback` / `releaseFeedback`，并恢复注册驱动的按压缩放动画
- SoundPool 改为显示时异步加载，加载完成后处理首次点击反馈
- 独立插件升级到 `0.3.0`，宿主仓库删除重复的鲸鱼包副本
