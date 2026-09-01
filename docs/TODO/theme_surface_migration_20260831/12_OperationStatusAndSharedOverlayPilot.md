# Operation Status 与共享 Overlay 试点

## 原状

`overlay.menu` 与 `overlay.snackbar` 已在 V2 surface catalog 和主题包中声明，但没有生产 consumer。`ThemeOverlaySurfaceHostV2` 只映射 dialog、sheet 与 toast。Package Manager 的 shared snackbar 和仓库市场发布页的普通 category menu 仍由 Material 容器固定绘制。

`status` 只声明 normal skin。`NativeThemeOperationStatusV1` 的 loading 已经由 `state.loading` 消费 status normal，但 success/error 仍使用 Material primary/error card，因此默认与 Cyber Grid 包无法定义 operation result 的 frame 和 content color。

`2.1.0` 主题包仅为开发预览。本批可以收紧 V2 包契约，不保留旧 preview archive 的激活路径。

## 本批范围

1. 将 overlay host 映射补齐：
   - `overlay.menu` -> `menu`
   - `overlay.snackbar` -> `snackbar`
2. 迁移两个保留原行为的生产试点：
   - Package Manager 的 `SnackbarHost` item。
   - Repo Market publish 页的普通 category `DropdownMenu`。
3. 将 `NativeThemeOperationStatusV1` 的 success/error body 改为 `status` normal/error skin；保留 loading renderer 与全部 V1 public Compose contract。
4. 将 direct `status` skin 的 error state 设为 archive/linker/package-script 强制契约，并同步 default、Cyber Grid、bundled default archive 与 Cyber basis。

## 明确不在范围

- 不批量迁移其余 `AlertDialog`、`DropdownMenu`、`ExposedDropdownMenu`、`SnackbarHost` 或 Android `Toast`。
- 不改变 Popup 定位、dismiss、队列、duration、action、focus、IME、gesture 或 accessibility 的调用方所有权。
- 不迁移 `state.empty`、`state.error` surface、detached host、plugin DSL、WebView DOM 或系统 UI。
- 不执行测试、APK 构建或发布，除非用户另行明确要求。

## 验收

- 两个 overlay surface 都有匹配 component skin 的生产 consumer；试点调用方的菜单选项与 snackbar action/dismiss 语义不变。
- success 使用 `status.normal`，error 使用 `status.error`，保留 live region、标题、message 与 decorative leading slot 语义。
- archive validator、linker 和两个主题 package script 都拒绝 direct `status` skin 缺少 error state 的归档。
- default/Cyber archive 与 bundled default lock 使用同一组精确摘要；Cyber basis 指向新的 default archive。

## 最小功能单元

[DONE] 1. 补齐 menu/snackbar overlay host 与 host tests。

[DONE] 2. 迁移 Package Manager snackbar 与 Repo Market category menu。

[DONE] 3. 收紧 status.error 契约、迁移 result renderer，并更新主题源与归档锁。focused tests 尚未执行，新的 archive 尚未发布。
