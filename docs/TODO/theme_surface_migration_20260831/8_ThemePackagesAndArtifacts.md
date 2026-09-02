# 主题包与归档

> [SUPERSEDED] schema 3 artifact 描述已被 [schema 4 验收](../theme_parameter_contract_rebuild_20260901/5_TestingAndAcceptance.md) 取代。

## 默认主题

`operit.default` 继续作为 APK 内置的精确 `.otheme` 归档。它必须为所有 surface 和 component skin 提供可见、可用、非赛博化的默认视觉，并通过与外部导入包相同的 validator、SHA-256 lock 和链接流程。

## 赛博主题

`operit.cyber_grid` 继续以外部可导入包存在。它覆盖 page、navigation、section、list、button、input、dialog、sheet、menu、snackbar、status 的 V2 skin。复杂 surface 只有在 host contract、renderer 和 `ThemeSurfaceHostPolicyV2` 同步扩展后才能添加 scene。

## 版本策略

[SUPERSEDED] 第一批只让现有 `TEMPLATE` 与 `HOST_SHELL` 获得 runtime consumer，不新增 V2 manifest 字段。

当前 schema 3 package document 允许主题作者声明 color palette 与 image picker 参数，并用受限 accent/token/stage effect 指定真实视觉目标。`ThemeSurfaceHostPolicyV2` 继续把每个 surface 的 host kind 固定为应用契约。

先前安装但不符合 host kind 契约的外部包保留在安装目录且显示为不可启用。它们不进入 runtime index；若曾被选中，启动会恢复 APK 内置默认主题，避免应用因一个失效包无法启动。

[DECISION] GitHub `2.1.0` 主题资产仅作为开发预览，不构成兼容发布接口。全 UI host contract 以当前开发基线为准，主题作者需要按 `ThemeSurfaceHostPolicyV2` 重新打包不符合该契约的开发包。

[DONE] 默认与赛博主题源的 `scripts/package.sh` 已校验 direct surface 的 host kind、同名 scene ID、未知 ID 与 skin ID。无 basis 的默认包还必须完整覆盖 37 个 surface 和 16 个 component skin，防止生成应用 runtime 会隔离的 archive。

[DONE] `input.focused`、`input.error` 与 `status.error` 都已是生产 required state。archive validator 拒绝不完整的 direct input/status skin，linker 拒绝继承链合并后不完整的 skin；默认与赛博主题源的 package script 同步检查这些状态。

[SUPERSEDED] Input interaction-state preview 已发布：[`operit.default`](https://github.com/luojiaping/operit-theme-default/releases/tag/input-preview-20260901-48ecba0) 的 SHA-256 为 `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`；[`operit.cyber_grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases/tag/input-preview-20260901-312e6a4) 的 SHA-256 为 `e60316ce282ffd7b035645217647ad28a67b9a575ad975841e5b59d6a17b0b1e`。该 preview 未声明 `status.error`，不再是当前开发基线。

[SUPERSEDED] `status.error` contract 开发 archive：`operit.default@2.1.0` SHA-256 为 `686bc48d09752de21a25a8abf6bf35246371816849da4ef33534f96bc1c9c964`；`operit.cyber_grid@2.1.0` SHA-256 为 `36e9180ead6b2d10225819a5b1774189f025a753fe945d8490d347e2e2161488`。

[DONE] 当前 schema 3 开发 archive：`operit.default@2.2.0` SHA-256 为 `dba4169c8e1636c5f2d85749f7770b8657d99217764563aac0302137e37ef4fd`，已同步 APK asset 与 runtime lock；`operit.cyber_grid@2.2.0` SHA-256 为 `afdd126f8a22dd6ce3c78ab566a17225d71dfa8db0704f1a09ee66c7e6a519f2`，basis 精确指向新的 default archive。两者尚未发布为新的 GitHub preview。

当后续新增复杂 scene、资源或 token 时，再以新 package version 发布默认包与赛博包，并按顺序更新：

1. 默认主题 archive 和 SHA-256
2. APK asset 与 `ThemePackageDefaultV2` lock
3. 赛博主题 basis
4. 赛博主题 archive 和 GitHub prerelease/release

## 验收

- default archive 永远存在于 APK asset 且坐标与摘要一致。
- cyber package 的 basis 精确指向当前默认 archive。
- manifest 覆盖清单、route binding 和实际 renderer 消费清单一致。

## 进展

[TODO] 建立 surface consumer coverage report。

[TODO] 在需要新 token/scene 时更新两个主题仓库归档。
