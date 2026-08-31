# 01 web 侧主题类型扩展与渲染对齐

原本状况：web-chat 的 WebThemeSnapshot 类型与渲染只覆盖 app 主题能力的一部分，
部分渲染语义与 app 不同，代码块等区域颜色写死，导致作为预览器时结果失真。

意图：web 侧类型加可选字段并对齐渲染逻辑；真机旧快照缺字段时按 app 默认值渲染。
不修改 app 侧代码，字段下发列为 app 侧跟进项。

## web 侧修复清单

以下编号沿用差异调查清单，严重度取自调查结论。web 表示
web-chat/src/ui/features/chat，app 表示 app/src/main/java/com/ai/assistance/operit。

### P0 阻塞预览

| 编号 | 差异 | web 侧动作 |
| --- | --- | --- |
| A1 | 背景模糊未渲染，app 有 use_background_blur 与 background_blur_radius 默认 10 | 类型加可选 blur；渲染层对背景 div 加 filter: blur(radius px)，默认值 10 |
| A2 | 视频背景不可播放，web 全库无 video 元素 | 类型加可选 muted、loop；背景层视频壁纸渲染 video 元素 |
| A4 | 气泡九宫格 20 个参数缺失，app 为 BubbleImageStyleConfig 九宫格绘制 | WebBubbleImageTheme 加 crop、repeat、scale 字段；CSS 用 border-image 九宫格实现 |
| A5 | render_mode 语义错配，web 判断 repeat 而 app 取值 tiled_nine_slice 与 nine_patch；web 还叠加固定暗色渐变 | 对齐取值枚举；去掉暗色渐变 |
| A10 | 气泡级独立字体缺失，app 每个气泡可单独设置字体 | 类型加气泡级字体字段；chatTheme 输出气泡级 font-family 变量 |
| A17 | 代码块写死 VSCode 暗色，--chat-code-block-bg 零消费 | 代码块背景接语义色变量，从 palette 派生亮暗两套 |
| B26 | chat-screen-frame 限宽 980px，app 全屏宽 | 限宽改为外壳可配置，模拟器按设备宽度渲染 |

### P1 明显可见

| 编号 | 差异 | web 侧动作 |
| --- | --- | --- |
| A7 | 输入栏玻璃启用条件缺 transparent 前置 | 对齐 app 条件 chatInputTransparent && liquid && !water |
| A8 | input transparent 渲染为半透明毛玻璃，app 是真 alpha 0 | is-transparent 改真透明 |
| A11 | fontScale 只作用于消息正文，app 缩放整个 Typography | --chat-font-scale 作用域扩展到 header、输入栏、弹窗 |
| A13 | header 历史与 pip 图标颜色不可配，app 支持自定义 | 类型加可选字段，缺省 --chat-header-icon-muted |
| A15 | palette 缺 tertiary、error、errorContainer、secondaryContainer、onSecondary、surfaceContainer 系列；token 环颜色写死 | 类型补语义色可选字段；token 用量环、发送队列、取消按钮按 app 阈值接 palette |
| A16 | on_primary 与 on_secondary 由 web 自算，阈值 0.58 且忽略 onColorMode，app 阈值 0.5 且支持强制黑白 | 快照提供时直接使用；缺失时对齐 app 阈值与 onColorMode |
| A18 | diff 与状态语义色写死 | 接 palette 派生：增 primaryContainer、删 errorContainer、行号 secondaryContainer |
| A19 | use_system_theme 下 web 明暗判断只看 theme_mode，palette 可能反向 | 渲染统一以快照 palette 亮度为准派生阴影、遮罩、玻璃参数 |
| A20 | 消息 token、耗时、时间戳与变体切换器整体缺失 | 补 MessageFooterBar：display 开关已下发；变体切换器需要消息 variants 数据，mock 提供 |
| B1 | classic 处理进度条恒定渐变与假进度 | 对齐 app 阶段变色；mock 驱动真实进度值 |
| B5 | agent 输入卡底色公式缺暗色 lerp(surface, onSurface, 0.08) | 底色公式对齐，暗色用 color-mix 派生 |
| B6 | agent 输入卡顶部高光恒定 box-shadow | 暗色 topEdgeHighlight、亮色 outerDiffuseShadow 分别实现 |
| B11 | 头像 fallback 用首字母，app 用 Person 与 Assistant 图标 | fallback 改图标形态 |
| B21 | 侧栏底色用 surfaceContainer，app 是 surface 0.95 | 底色 token 改 surface |

### 不修项

- A3 共 12 项 app bar、drawer、状态栏专属配置：web 无对应 surface，预览器标注不适用
- B23、B24 液态玻璃与水玻璃机理差异：参数体系无法一一映射，接受近似并文档标注
- 13 项细微差异：见调查存档，默认不处理

## app 侧跟进项（不在本分支实施）

buildThemeSnapshot 需要补下发：背景模糊两项、视频 muted 与 loop、气泡九宫格
20 参数与 render_mode 枚举、气泡级双字体、palette 语义色六项、on_primary 与
on_secondary、onColorMode、header 图标色两项、show_chat_floating_dots_animation。
位置在 WebChatModels.kt 与 WebChatHttpBridge.kt 的 buildThemeSnapshot。

## 期待的新实现状况

web-chat 在快照缺字段时与真机行为一致，mock 快照带全字段时可预览 app 全部主题
能力；差异修复不引入运行时兜底逻辑，字段缺失一律走显式默认值表。
