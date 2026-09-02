# 运行时投影与宿主

## 单一解析结果

主题实例值在链接后的 immutable package runtime 上解析为 token、Typography、scene media、component presentation、chat presentation、Composer presentation 和 App Chrome presentation。运行时不读取设置 UI 的字段 ID，也不修改 archive 的静态内容。

## 宿主

- Android Compose 根、app shell、chat scene、Bubble、Cursor、两种 Composer 和系统 chrome
- 离屏消息图片
- WebChat HTTP snapshot 与 TypeScript chat runtime

消息诊断和展示行为继续由 GlobalPresentation 管理，并保持在独立页面。它们不作为 package parameter effect。

## 最小功能单元

[DONE] 1. 完成 schema 4 解析、token/component/scene 投影和 owner 校验。

[DONE] 2. 让 Android 主题宿主、聊天与 Composer 读取相同的 resolved presentation。

[DONE] 3. 让离屏与 WebChat 使用同一份 presentation 结果与可访问资源 URL。
