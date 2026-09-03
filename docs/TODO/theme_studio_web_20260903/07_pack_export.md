# 07 打包导出

## 现状

无 `.otheme` 导出；`zipWriter.ts` 为 store-only 定制（结构正确、无 comment 写入、无 deflate、无 assets 清单）。

## 意图

导出为可被 app `ThemePackageArchiveValidatorV2` 接受的 `.otheme`：严格序列化 + 素材 assets 清单 + ZIP 元数据；导出后可自检回环（重新导入通过 02 校验）。

## 期待

- manifest 序列化：
  - 字段集精确（无未知键；可空可选字段语义与 kotlinx `explicitNulls=false` 一致——缺失=默认值，不输出 null 键）。
  - `presentation.behavior` 完整输出（validateExplicitPresentationBehavior 集合）；`basis`（新建包必带，导入包保留原值或用户改选）。
  - parameters：复制原定义，作者编辑值烘焙规则——
    - 非 URI：写入 `defaultValue`（值域/控件匹配校验）。
    - URI 类：不烘焙；若绑定素材，素材写入 `assets[]`（key/path/kind/mime/sha256/byteSize），参数保留"无默认"（导出后真机需自选，与 schema 4 语义一致）；导出侧展示提示清单。
- ZIP 写入：entry 顺序确定性、UTF-8 名、store/deflate（deflate ≥64KB 条目）、ZIP comment 精确 `Operit Theme Package`。
- 素材 path：`assets/<ext>/<key>` 目录布局；重复 key 拒绝；byteSize/SHA 由 writer 实际计算并回填（保证清单与内容一致）。
- 导出 UI：命名（`.otheme`）、下载、导出前 diff 摘要（05 差异）+ 未烘焙 URI 提示；导出后同包再导入自动 smoke（后台跑 02 校验，失败红显）。
- 输出 sha256（供 basis 引用复制）。

## 最小功能单元

[DONE] 1. manifest 序列化器（strict 字段集/显式默认值）+ 烘焙规则（非 URI 值、URI 提示清单）（`exporter.ts`）。

[DONE] 2. zipWriter 增强：deflate、comment、确定性顺序、UTF-8、尺寸回填；round-trip 自测（写→读→逐项比对）（`zipWriter.ts` + `tests/exporter.test.ts`）。

[DONE] 3. 导出控制器：素材写盘清单、SHA/byteSize 计算回填、`basis` 坐标写入、schemaVersion=4。

[IN PROGRESS] 4. 导出 UI + 后台 re-validate smoke + sha256 展示：导出按钮、sha256 展示、导出后 `smokeValidateExportedArchive` 回环自检已接；导出前 diff 摘要 UI 未接。

## 旧实现

无 JSON 序列化；zipWriter 无 comment/deflate；无素材进入产物。
