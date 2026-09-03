# 02 包导入与解析校验

## 现状

`toolpkgLoader.ts` 有 EOCD 读取但忽略 comment-length 字段、无压缩条目边界校验，且仅用于 toolpkg；`.otheme` 无读取路径。

## 意图

首步工作流：拖放/选择 `.otheme` → 提取根 `operit-theme.json` → 全链路校验 → 生成编辑态内部模型（参数定义树、素材清单、basis 坐标）。

## 期待

- 独立 ZIP 读取模块（复用/抽取 `toolpkgLoader` 的 EOCD/CP 解析）：
  - 支持 store + deflate 条目、UTF-8 文件名、ZIP comment 读取。
  - bounds 校验：EOCD comment-length、中央目录偏移/大小、条目 dataOffset+compressedSize ≤ 文件长。
- 导入页：拖放+文件选择；读取后立即展示 packageId/version/displayName/basis/参数数/素材数，含结构化错误面板（字段路径）。
- 编辑态模型 `StudioPackage`：manifest（不变量：校验通过后不允许非编辑路径变更）+ 素材引用（`Map<assetKey, AssetRecord>`）+ 参数值状态（`Map<paramId, Value | unset>`，初始=pack 默认）。
- 校验失败不产生编辑态；错误列表可展开定位。

## 最小功能单元

[TODO] 1. ZIP 读取模块：EOCD/CP 解析加固（comment-length、bounds）、deflate 支持、路径安全（拒绝 `/`、`\`、`..`）。

[TODO] 2. 导入控制器：`.otheme` → strict 解码 + `validation.ts` → `StudioPackage`；对 default/cyber `3.0.0` 完成冒烟。

[TODO] 3. 导入 UI：拖放/选择、元信息展示、错误面板（聚合+字段路径）。

## 旧实现

无 `.otheme` 导入；toolpkgLoader 仅覆盖 toolpkg 场景且存在边界缺陷。
