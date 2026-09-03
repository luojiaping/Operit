# 04 素材管线

## 现状

`ThemeStudioPanel` 支持背景图片填入（仅内存态、无校验、无资产模型）；mock 无素材层。

## 意图

素材上传作为一级能力：受控校验 → 素材库持久化 → 可被 URI 参数绑定与预览引用 → 导出时写 `manifest.assets` 声明。

## 期待

- 上传校验（对齐 app 生产约束）：
  - mime 白名单：image/jpeg·png·webp / video/mp4·webm / font/ttf·otf。
  - 单条目 ≤48MB、解压总量 ≤64MB、条目数 ≤512（按 app `ThemePackageArchiveValidatorV2` 上限）。
  - 图片解码校验（浏览器 `createImageBitmap` 或 canvas）、视频头（`ftyp`/EBML）、字体头（ttf/otf magic）。
  - asset key 生成（member ID 规则）+ 路径 portable 规则；自动计算 SHA-256、byteSize。
- 素材库：IndexedDB 持久化（blob + 元数据 + SHA）；预览期 blob URL 注册表（显式释放在替换时）。
- 素材分类视图（IMAGE/VIDEO/FONT）+ 预览（图片缩略、视频可播、字体名预览）+ 删除/替换（替换时同步解除参数绑定）。
- 绑定接口：URI 系控件（image/video/font_picker）从素材库选择；值记录为素材引用（资产 key 的内部形态，UI 层表现为素材条目）。
- 素材不进入包默认值；仅导出 `assets[]`（见 07）。

## 最小功能单元

[TODO] 1. 素材校验器（mime/头签名/大小/重复 key/路径）+ 单元测试样例集。

[TODO] 2. 素材库存储（IndexedDB）+ blob URL 注册表 + 生命周期（替换即 revoke）。

[TODO] 3. 素材管理 UI（分类/预览/删除/替换）+ URI 控件绑定选择器。

[TODO] 4. `StudioPackage` 内素材模型（AssetRecord: key/path/kind/mime/sha256/byteSize/blob）与校验集成。

## 旧实现

无校验、无持久化、无 assets 模型；仅内存背景 URI 注入。

## 本轮进展

[IN PROGRESS] 导入包素材会登记到浏览器素材库；目标 Inspector 新增统一素材区，支持图片/视频/字体上传、素材绑定、删除和内置素材保护。IndexedDB 操作改为每次事务重新取得 object store，避免跨事务复用失效句柄。
