# 04 输入插槽注入与 Compose DSL 渲染器

原本状况：web-chat 完全没有 slot 机制；app 侧三个插槽在 agent 与 classic 两种
输入风格有明确注入位与布局参数。

意图：web 侧按 app 相同位置注入三个插槽，并提供浏览器端 Compose DSL 渲染器，
使插槽内容可预览。

## 注入位对照

- above_input：输入区容器顶部、PendingMessageQueuePanel 之前，agent 与 classic
  同位，padding 水平 12dp agent 与 16dp classic、垂直 4dp
- input_drawer：输入卡内部、输入框正上方，fillMaxWidth
- input_toolbar_right：agent 在模型 pill 之后宽度上限 180dp；classic 在文本框
  之后加号按钮之前，同样 180dp 上限

## DSL 渲染器

- screen 脚本是纯 JavaScript，浏览器原生可执行，不需要 QuickJS
- ctx shim 提供 UI 注册表、useState、h、Modifier 代理与 MaterialTheme token
- ComposeNode 树映射为 React 组件，首期白名单：Card、Column、Row、Box、Text、
  Button、Icon、Spacer、Switch、LinearProgressIndicator
- Text 结果按 app 渲染为 bodySmall 与 onSurface
- 重解析键对齐 app：inputText 不触发重渲染
- 渲染为预览近似，组件间距与 Compose 存在差异，标注在预览站界面

## 期待的新实现状况

examples/input_slot_demo 的三个 slot 在 web 预览中的位置、层级与 app 一致，
DSL 卡片与文本可见。
