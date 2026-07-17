# 字符串资源工具

Android 的中文字符串资源位于
`app/src/main/res/values/strings.xml`，其他语言目录以它作为 key 和占位符基准。

## 检查

在仓库根目录运行：

```bash
python3 ci/script/check_localizations.py
python3 tools/string/check_strings.py --simple
```

`check_localizations.py` 是 CI 使用的结构检查，负责检查 XML、重复 key、资源类型和
占位符。已有的缺失翻译和相同译文会作为提示；本次修改造成的占位符不一致会阻止检查。

`check_strings.py` 是本地诊断工具，用于查看各语言文件的 key 数量、重复项和缺失项。

## 翻译

`fill_missing_translations.py` 可以生成缺失翻译报告，也可以调用配置的翻译服务补全文件：

```bash
python3 tools/string/fill_missing_translations.py --report-only
python3 tools/string/fill_missing_translations.py --dry-run --limit 20
```

写入翻译文件前请检查生成结果，保留 `%1$s`、`%2$d`、`{name}` 和换行等占位符。完整的
贡献流程见 [贡献指南](../../docs/doc-src/dev-core/CONTRIBUTING.md)。
