# 主题仓库与发布

## 默认主题

`luojiaping/operit-theme-default` 是 V2 默认主题的唯一源。它实现全部 required daily surface 与通用组件皮肤。APK 仅内置其精确 source artifact 与 SHA-256 lock；稳定发布时该 artifact 必须与对应 Release asset 字节一致。

## 赛博主题

`luojiaping/operit-theme-cyber-grid` 是赛博主题的唯一源。它显式依赖默认主题的精确 V2 坐标，并覆盖 shell、chat、component skins 与资源。它永不进入 APK；用户从 GitHub Release 导入 `.otheme`。

## 发布

两仓库的 package 脚本必须验证 schema 3 manifest、全部资源摘要、required coverage、参数 control/effect、确定性 ZIP metadata 与 `Operit Theme Package` comment。只从 release tag 产出 `.otheme` 与 SHA-256 assets；release notes 必须记录 package archive SHA-256，Cyber Grid 还必须记录 basis 精确坐标。

## 进展

[SUPERSEDED] V2 `v2.0.0` 的 token 命名修正归档已被当前 component-frame 开发基线取代。

[SUPERSEDED] 初始默认主题开发预览：`operit.default@2.1.0`，SHA-256 `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`。

[SUPERSEDED] 初始赛博主题开发预览：`operit.cyber_grid@2.1.0`，SHA-256 `e60316ce282ffd7b035645217647ad28a67b9a575ad975841e5b59d6a17b0b1e`。

[SUPERSEDED] `status.error` contract 开发基线：`operit.default@2.1.0` SHA-256 `686bc48d09752de21a25a8abf6bf35246371816849da4ef33534f96bc1c9c964`；`operit.cyber_grid@2.1.0` SHA-256 `36e9180ead6b2d10225819a5b1774189f025a753fe945d8490d347e2e2161488`。

[DONE] 当前 schema 3 开发基线：`operit.default@2.2.0` SHA-256 `dba4169c8e1636c5f2d85749f7770b8657d99217764563aac0302137e37ef4fd` 已内置 APK；`operit.cyber_grid@2.2.0` SHA-256 `afdd126f8a22dd6ce3c78ab566a17225d71dfa8db0704f1a09ee66c7e6a519f2` 的 basis 精确指向该默认坐标。二者尚待新的 GitHub preview 发布。

[DECISION] `2.1.0` GitHub prerelease 仅是开发预览，不构成长期主题包接口。全 UI surface host contract 以当前开发基线为准，正式发布前会重新打包并记录新的精确坐标。
