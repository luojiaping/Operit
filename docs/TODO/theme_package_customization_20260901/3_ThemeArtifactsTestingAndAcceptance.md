# 主题包、测试与验收

> [SUPERSEDED] schema 3 artifact 记录已由 [schema 4 参数契约验收](../theme_parameter_contract_rebuild_20260901/5_TestingAndAcceptance.md) 取代。本文件仅保留历史构建信息。

## 主题源

默认与 Cyber Grid package document 同步升级 schema 3、版本 2.2.0。默认包声明 `accent_color` 与 `background_image`；Cyber Grid 不继承 default 的活动 effect，继续由自己的 scene、token 和 component frame 定义视觉。

更新顺序：default archive、APK asset 与 SHA lock、Cyber basis、Cyber archive、主题仓 release metadata。新的 preview release 只在用户明确要求后发布。

## 测试

- JVM：schema/control/effect validation、basis ownership、parameter resolution、token overlay、选择值校验。
- Android Compose：accent 对 Material/component/scene 的像素结果，stage image 图层顺序，设置页语义与即时写入。
- Device：手机/平板、深浅模式、字号、背景图、聊天样式、输入样式、Cyber Grid 视觉保持。

## 最小功能单元

[DONE] 1. 更新 default/Cyber manifest、package script、APK asset、basis 与 lock。default `2.2.0` SHA-256 为 `dba4169c8e1636c5f2d85749f7770b8657d99217764563aac0302137e37ef4fd`；Cyber `2.2.0` SHA-256 为 `afdd126f8a22dd6ce3c78ab566a17225d71dfa8db0704f1a09ee66c7e6a519f2`。

[DONE] 2. 补齐 JVM/Android tests 与静态 coverage。新增 selection migration、parameter owner、runtime token/stage overlay、bundled parameter、设置内容和窄屏布局测试；测试命令尚未执行。

[DONE] 静态 artifact 验证：两个 `package.sh` 均通过 shell/JQ、确定性 ZIP、ZIP comment 和 `sha256sum -c`；APK 内置 default archive 的 ZIP、schema、参数清单和 runtime lock 均与 default source archive 相同；Cyber manifest 与 archive basis 均精确指向该 default SHA-256。未执行 Gradle、JVM 或 Android test。

[DONE] 3. 构建服务已同步 `172905cb` 并完成 release 编译与旋转签名：`operit-release-feat_plugin-interface-172905cb.apk`，SHA-256 `246e1d1fbe6a70527798f396796b6ed2bf24669468a4743e5bc9b3cdace4034b`，大小 403,117,896 bytes。未执行 JVM/Android test。

[TODO] 4. 设备验收和新的 GitHub preview release。
