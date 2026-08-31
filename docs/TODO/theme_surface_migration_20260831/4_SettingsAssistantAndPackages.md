# 设置、助手与包管理

## 目标 surface

- `settings.index`
- `settings.form`
- `settings.statistics`
- `assistant.profile`
- `persona.card_studio`
- `prompt_tag.market`
- `packages.manager`

## 页面范围

- Settings index、模型配置、聊天历史、备份恢复、主题包、token 统计
- Persona card、prompt tag、角色与助手资料
- Package manager、Skills、MCP、插件管理与详情

## 介入方式

这些页面以表单、设置行、统计卡、tab、列表、dialog 和 sheet 为主。优先复用 batch 2 primitive，不为每个设置项创建 scene。主题化重点是页面分区、切角列表项、输入框、状态反馈、危险操作与选择状态。

## 入口

- `ui/features/settings/screens/`
- `ui/features/settings/components/`
- `ui/features/packages/screens/PackageManagerScreen.kt`
- `ui/theme/renderer/`

## 风险与约束

- 备份、导入、删除和恢复动作保留原有确认与错误文案。
- 主题包导入页必须保持不受待导入主题影响的错误提示路径。
- 统计图表只主题化容器、图例和操作 chrome，不改变数据比例或手势。

## 验收

- 设置卡、列表、输入、确认 dialog、sheet 和进度状态消费 V2 skin。
- destructive 操作的错误与确认状态仍清晰可辨。
- 主题包导入、启用、删除和无效包处理可正常完成。

## 进展

[DONE] Settings index 的 section 与导航行已消费 `section`、`list_item` skin，原生导航操作保持 button 语义。

[DONE] `CustomEmojiManagementScreen` 的 root Scaffold 已改为透明并继承 page host content 色，避免覆盖 `settings.form` page skin。

[DONE] Theme packages route 已由 `settings.form` page host 承载；已安装主题条目消费 `list_item` normal/selected/disabled skin，选择继续使用 RadioButton 语义。

[PARTIAL] Backup 的 shared section、standard action 与 choice renderer 已迁移到 V2 skin。其余 settings/assistant/packages route 家族仍待逐页接入。

[PARTIAL] `PackageItem` shared renderer 已由 `list_item` skin 绘制；当前主 `PackageManagerScreen` 生产列表尚未接入该 renderer，插件/包/技能/MCP 主列表迁移仍待完成。
