# 主题仓库与发布

## 默认主题

`luojiaping/operit-theme-default` 是 V2 默认主题的唯一源。它实现全部 required daily surface 与通用组件皮肤。APK 仅内置其精确 Release artifact 与 SHA-256 lock。

## 赛博主题

`luojiaping/operit-theme-cyber-grid` 是赛博主题的唯一源。它显式依赖默认主题的精确 V2 坐标，并覆盖 shell、chat、component skins 与资源。它永不进入 APK；用户从 GitHub Release 导入 `.otheme`。

## 发布

两仓库的 package 脚本必须验证 V2 manifest、全部资源摘要、required coverage、确定性 ZIP metadata 与 `Operit Theme Package` comment。只从 release tag 产出 `.otheme` 与 SHA-256 assets。

## 进展

[SUPERSEDED] V2 `v2.0.0` 的 token 命名修正归档已被当前 component-frame 开发基线取代。

[DONE] 默认主题开发预览：`operit.default@2.1.0`，SHA-256 `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`；该 archive 固定内置于 APK。

[DONE] 赛博主题开发预览：`operit.cyber_grid@2.1.0`，SHA-256 `e60316ce282ffd7b035645217647ad28a67b9a575ad975841e5b59d6a17b0b1e`，basis 精确指向上述默认坐标。

[DECISION] `2.1.0` GitHub prerelease 仅是开发预览，不构成长期主题包接口。全 UI surface host contract 以当前开发基线为准，正式发布前会重新打包并记录新的精确坐标。
