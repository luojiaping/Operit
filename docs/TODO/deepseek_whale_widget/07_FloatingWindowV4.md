---
title: 固定视口与媒体反馈重做
status: in_progress
---

# 固定视口与媒体反馈重做

## 反馈问题

- `SoundPool` 使用系统提示音 usage，设备的提示音或免打扰设置会让点击反馈无声
- 资源缓存不保留源文件扩展名，且旧缓存不会被重新写入
- 窗口尺寸、`routeArgs.scale`、字体、偏移和内容框同时缩放，气泡内容出现二次缩放
- 显式 `fontSize` 仍继承主题的行高，放大后的文字行框失配

## 测试期约束

- 当前版本未正式发布，不保留 `toolpkg.floating_window.v3` 接口、存储或 ToolPkg 包兼容逻辑
- 删除误发布的 `v0.3.0` GitHub Release 和 tag
- 宿主不识别鲸鱼、气泡、DeepSeek 或插件资源名称

## v4 契约

```ts
interface FloatingWindowContentLayout {
  mode: "fixed";
  widthDp: number;
  heightDp: number;
  scaleMode: "fit";
}

interface FloatingWindowRegistration {
  contentLayout: FloatingWindowContentLayout;
  pressFeedback?: FloatingWindowFeedback;
  releaseFeedback?: FloatingWindowFeedback;
}
```

- 宿主按最终窗口大小对固定设计视口做一次 `fit` 缩放
- 插件仅使用设计坐标，不能通过 route arguments 再次缩放字号、偏移、路径或内容框
- 音频缓存按资源原始文件名写入，使用媒体流异步准备与播放
- `Text` 和 `BasicText` 支持显式 `lineHeight`

## 完成条件

- `toolpkg.floating_window.v4` 从注册 parser、DTO、桥接、service、JavaScript facade、类型和文档贯通
- 新偏好命名空间不读取 v3 窗口位置、尺寸、音量或反馈配置
- v4 音频资源加载、准备、播放和错误均有宿主日志
- 气泡在 `0.6x - 2.5x` 只发生一次整体缩放，文字、GIF、描边和路径保持比例
- 已删除 v0.3.0 公开 Release，下一次 ToolPkg 只作为测试预发布发布

## 实现记录

- 已删除 `v0.3.0` GitHub Release、tag 和本地缺陷归档
- capability 改为 `toolpkg.floating_window.v4`，`contentLayout` 为必填固定设计视口
- 新偏好命名空间 `toolpkg_floating_windows_v4_fixed_viewport` 不读取 v3 窗口状态
- 宿主在 `contentLayout` 内提供统一设计 density、单次 `fit` 缩放和固定字体比例
- 媒体反馈改用 `MediaPlayer` 与 `USAGE_MEDIA`，资源每次按源文件名重新物化后异步准备
- Compose DSL 新增 `lineHeight`，Canvas 描边支持带单位的数值
- 插件删除 `routeArgs.scale`、逐项字体缩放和逐项内容偏移缩放，改为固定设计坐标
- 待执行远程 Release 编译、测试包归档和设备验证
