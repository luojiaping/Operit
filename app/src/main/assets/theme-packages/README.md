# Bundled Theme Artifacts

This directory contains only release artifacts that must ship inside the APK.

`operit-default-v2.otheme` is copied byte-for-byte from the `operit-theme-default` `2.2.0` schema 3 development artifact while the V2 surface contract is under active development.

- Package ID: `operit.default`
- Version: `2.2.0`
- Schema: 3 (`operit-theme.json` schemaVersion 3)
- SHA-256: `dba4169c8e1636c5f2d85749f7770b8657d99217764563aac0302137e37ef4fd`
- Source artifact: `operit-default-2.2.0.otheme`

The package source, manifest and release workflow live only in the external repository. Replace this development artifact with the byte-identical upstream Release archive before publishing, then retain the exact SHA-256 lock in `ThemePackageDefaultV2`.

`operit.cyber_grid` is intentionally absent. Its standalone release artifacts live in [`luojiaping/operit-theme-cyber-grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases) and users import them through the Themes screen.
