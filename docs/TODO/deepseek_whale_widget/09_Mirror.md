---
title: 鲸鱼悬浮窗镜像
status: in_progress
---

# 鲸鱼悬浮窗镜像

## 问题

- 上游原版默认停在右下角，鲸鱼朝左；拖到左边缘吸附时整体水平镜像翻转，
  文字和 gif 反向翻回保持可读
- Operit 插件当前的鲸鱼和气泡窗口没有镜像能力，拖到左侧时朝向错误

## 修改意图

- 跟随上游行为：默认右侧不镜像，窗口停靠在屏幕左半边时自动镜像
- 设置页提供手动开关：自动 / 开 / 关
- 鲸鱼和气泡两个窗口一起镜像，翻转是瞬时切换
- 宿主保持通用：不识别鲸鱼、气泡或任何业务名称

## 宿主改动

- 浮窗状态新增 `widthPx`、`heightPx`、`screenWidthPx`、`screenHeightPx`，
  插件据此判断窗口停靠侧
- 拖拽松手、吸附落位和尺寸调整结束后重渲染窗口及其跟随窗口
- `Modifier.scale` 支持双参数 `scale(scaleX, scaleY)`，用于水平镜像

## 完成条件

- 鲸鱼和气泡拖到左半边自动镜像，右半边恢复
- 设置页镜像开关可在自动、开、关三态间切换
- 气泡文字和 gif 镜像后保持可读
- `src` 和 `dist` 内容一致
- 待执行设备上的镜像截图验证

## 实现记录

- 宿主浮窗状态新增 `widthPx`、`heightPx`、`screenWidthPx`、`screenHeightPx`
- 拖拽松手、吸附落位和尺寸调整结束后宿主重渲染窗口及其跟随窗口
- Compose DSL `Modifier.scale` 支持 `scale(scaleX, scaleY)` 水平镜像
- 插件使用 `DEEPSEEK_WHALE_MIRROR_MODE`（`auto` / `on` / `off`）控制镜像，
  鲸鱼窗口根节点翻转，气泡文字和 gif 反向翻回保持可读
- 宿主提交 `014d8d3b7` 已通过远程 Release 编译
- 插件测试包 [`v0.4.0-test.3`](https://github.com/luojiaping/operit-deepseek-whale-widget/releases/tag/v0.4.0-test.3) 已上传
- 待执行设备上的镜像截图验证
