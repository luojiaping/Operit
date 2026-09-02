# 设置页与内置主题

## 设置页

Theme 页面管理选择、导入和已安装包。Appearance、Conversation、Composer 与 App Chrome 仅渲染活动 package 中标记为用户可见的参数。控件直接写入活动实例，单项可恢复主题默认值；没有全局草稿或保存状态。

消息展示项留在 Display & Behavior。界面采用平面分组和稳定行高，窄屏与大字号使用列表式单选，不创建历史编辑器预览或嵌套 Card。

## 内置主题

default 与 Cyber Grid 更新为 schema 4。两者都声明完整能力所需的 target 类型，并只公开精简的用户设置。Cyber 对自己的 token、素材、scene 和 component frame 声明 effect，不继承 default 的活动参数。

## 最小功能单元

[DONE] 1. 用 manifest visibility/group/order/condition 取代硬编码的两项设置。

[DONE] 2. 移除主题页中的消息展示项，保留其独立 Display & Behavior 入口。

[DONE] 3. 重打 default、更新 APK archive lock、更新 Cyber basis 与 archive。
