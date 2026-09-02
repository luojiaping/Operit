# 字段矩阵与产品边界

## 参数能力下限

`95619952` 的 111 个编辑字段是本批协议能力下限，不是用户设置页的行数要求。主题作者可将参数固定在 archive 中，也可以按可见性公开给用户。

| 基线分区 | 参数能力 | 默认用户页策略 |
| --- | --- | --- |
| Colors & Mode | 模式、调色板、语义 token、对比文字 | 主题模式与主题作者公开的高层颜色 |
| Typography | 系统/包/用户字体、字体缩放、文本角色缩放 | 字体族与高层缩放 |
| Background | 图片、视频、透明度、模糊、播放和场景布局 | 主题作者公开的背景媒体和强度 |
| Conversation | 样式、颜色、气泡素材、字体、图片布局、头像形状 | 聊天样式、头像和主题作者公开的视觉选项 |
| Composer | 输入样式、透明/浮动、材质 | 输入样式和主题作者公开的外观选项 |
| App Chrome | 状态栏、工具栏、导航、聊天顶栏及内容颜色 | 主题作者公开的高层 chrome 选项 |
| Message Details & Motion | 消息展示、诊断、活动反馈 | 独立 Display & Behavior 页面，不进入主题设置 |

## 协议映射

| 基线能力 | schema 4 值和 target |
| --- | --- |
| 自定义 primary、secondary、on-color 和独立深浅色 | `COLOR`、`COLOR_PAIR`，`accent_palette`、`token_color`、`token_color_pair` |
| 系统字体、用户字体和字体比例 | `OPTION`、`FONT_URI`、`FLOAT`，Typography target 与 package font cache |
| 图片/视频背景、透明度、模糊和播放 | `IMAGE_URI`、`VIDEO_URI`、`BOOLEAN`、`FLOAT`、`OPTION`，Background target 与 scene media |
| Cursor/Bubble 颜色、玻璃、宽度、圆角、字体、图片、裁剪和内边距 | color/resource/layout/inset values，Conversation target、component inset 与 Bubble renderer |
| 头像形状和圆角 | `OPTION`、`CORNER_RADIUS`，Avatar target |
| Composer 透明、浮动和材质 | `BOOLEAN`，Composer target 与 Composer surface |
| 状态栏、toolbar、导航、聊天顶栏和图标颜色 | color/boolean/option values，Chrome target 与 Android/Web host |
| component frame、九宫格和低层几何 | `FLOAT`、`INSETS`、author control，component frame/inset effect 和 archive 静态 scene/assets |

`chat_style`、`input_style`、主题模式和全局字号仍是全局展示设置。消息诊断和展示开关保持在 Display & Behavior。业务头像和聊天标题保持在角色或群组编辑器。

## 不进入普通主题设置页

- 角色或群组目标选择、目标级草稿、保存流程和隔离预览
- 气泡裁剪、重复区间、精确内边距、九宫格、frame 几何和玻璃实现细节
- 消息模型、token、耗时、身份、思维过程和状态展示开关

这些能力仍可由主题 archive 固定，或由主题作者按参数可见性显式公开。内置主题默认不把低层布局与材质细节放入用户设置页。

## 最小功能单元

[DONE] 1. 将基线字段映射为强类型 parameter value、control、target 与运行时投影。

[DONE] 2. 将用户可见性、分组、顺序和条件显示作为 manifest 契约，而不是 Settings UI 的硬编码字段列表。

[DONE] 3. 删除旧基线 UI 的目标、草稿、预览和命令交互，不重新引入对应状态模型。
