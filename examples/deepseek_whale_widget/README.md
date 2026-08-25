# DeepSeek Whale Widget

这是一个依赖 Operit 宿主桥接的 ToolPkg 示例包。

## 宿主要求

- DeepSeek host bridge DTO schema `2`
- `deepseek.accounts.v2`
- `deepseek.balance.v2`
- `deepseek.cached_snapshot.v2`
- `deepseek.platform_status.v2`
- `deepseek.platform_set.v2`
- `deepseek.platform_usage.v2`
- `deepseek.stats.v2`
- `toolpkg.floating_window.v1`

## 使用方式

1. 启用 `dsh-whale-widget` 插件
2. 在鲸鱼余额页面选择 DeepSeek 配置和 API Key
3. 点击刷新读取余额
4. 需要平台实时用量时，在页面中保存 DeepSeek platform token
5. 在页面中显式显示鲸鱼悬浮窗

平台 Token 由宿主加密存储，页面只负责提交设置动作，不显示已保存的 Token。浮窗可以长期驻留，隐藏时会释放插件的 Compose 和 JavaScript runtime。
