# Bundled Theme Artifacts

This directory contains only release artifacts that must ship inside the APK.

`operit-default-v4.otheme` is copied byte-for-byte from the `operit-theme-default` `3.0.0` schema 4 development artifact while the package surface contract is under active development.

- Package ID: `operit.default`
- Version: `3.0.0`
- Schema: 4 (`operit-theme.json` schemaVersion 4)
- SHA-256: `5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f`
- Source artifact: `operit-default-3.0.0.otheme`

The package source, manifest and release workflow live only in the external repository. Replace this development artifact with the byte-identical upstream Release archive before publishing, then retain the exact SHA-256 lock in `ThemePackageDefaultV2`.

`operit.cyber_grid` is intentionally absent. Its standalone release artifacts live in [`luojiaping/operit-theme-cyber-grid`](https://github.com/luojiaping/operit-theme-cyber-grid/releases) and users import them through the Themes screen.
