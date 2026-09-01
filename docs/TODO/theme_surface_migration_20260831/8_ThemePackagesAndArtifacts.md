# 主题包与归档

## 默认主题

`operit.default` 继续作为 APK 内置的精确 `.otheme` 归档。它必须为所有 surface 和 component skin 提供可见、可用、非赛博化的默认视觉，并通过与外部导入包相同的 validator、SHA-256 lock 和链接流程。

## 赛博主题

`operit.cyber_grid` 继续以外部可导入包存在。它覆盖 page、navigation、section、list、button、input、dialog、sheet、menu、snackbar、status 的 V2 skin。复杂 surface 只有在 host contract、renderer 和 `ThemeSurfaceHostPolicyV2` 同步扩展后才能添加 scene。

## 版本策略

第一批只让现有 `TEMPLATE` 与 `HOST_SHELL` 获得 runtime consumer，不新增 V2 manifest 字段。`ThemeSurfaceHostPolicyV2` 同时把每个 surface 的 host kind 固定为应用契约；官方 `2.1.0` 默认和赛博包符合该契约。

先前安装但不符合 host kind 契约的外部包保留在安装目录且显示为不可启用。它们不进入 runtime index；若曾被选中，启动会恢复 APK 内置默认主题，避免应用因一个失效包无法启动。

[DECISION] GitHub `2.1.0` 主题资产仅作为开发预览，不构成兼容发布接口。全 UI host contract 以当前开发基线为准，主题作者需要按 `ThemeSurfaceHostPolicyV2` 重新打包不符合该契约的开发包。

[DONE] 默认与赛博主题源的 `scripts/package.sh` 已校验 direct surface 的 host kind、同名 scene ID、未知 ID 与 skin ID。无 basis 的默认包还必须完整覆盖 37 个 surface 和 16 个 component skin，防止生成应用 runtime 会隔离的 archive。

[DONE] `input.focused` 与 `input.error` 已是生产交互 state。archive validator 拒绝不完整的 direct input skin，linker 拒绝继承链合并后不完整的 input skin；默认与赛博主题源的 package script 同步检查两者。

[DONE] Input interaction-state preview 已发布：[`operit.default`](https://github.com/luojiaping/operit-theme-default/releases/tag/input-preview-20260901-48ecba0) 的 `operit-default-2.1.0.otheme` SHA-256 为 `3ada292d108f11efaaa78e029db307229e2fada18ed15b4bd09a75b8323c8f13`；[`operit.cyber_grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases/tag/input-preview-20260901-312e6a4) 的 `operit-cyber-grid-2.1.0.otheme` SHA-256 为 `e60316ce282ffd7b035645217647ad28a67b9a575ad975841e5b59d6a17b0b1e`。两者均为 prerelease，坐标保持 `2.1.0`。

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
