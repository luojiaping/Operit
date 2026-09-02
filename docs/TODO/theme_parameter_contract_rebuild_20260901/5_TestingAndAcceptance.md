# 测试与验收

## 自动覆盖

- schema 4 value/control/effect compatibility、owner、条件和资源约束
- 基线字段能力矩阵和新 target 类型
- default/Cyber archive、basis、SHA lock 与 active-package isolation
- Settings visibility、分组、条件、窄屏和大字号
- Android、offscreen 与 WebChat presentation projection

## 验收

- 内置主题每项用户可见参数即时生效且可恢复 package default
- 低层参数不会在普通设置页出现
- 消息展示开关不出现在主题页
- schema 3 archive 与实例没有生产读取路径

## 最小功能单元

[PARTIAL] 1. 已添加 parameter value/condition/owner、空背景媒体、用户默认值与 bundled visibility focused tests；JVM、Compose 与 WebChat test 命令尚未执行。

[DONE] 2. 静态 archive 检查：default `operit.default@3.0.0` SHA-256 `5a96abf521ec22a8c486ccb5b4d7f561a4bed4f6f90dfe742647024eb79db91f`，Cyber `operit.cyber_grid@3.0.0` SHA-256 `77c65c812d81a3379edf53fb741e847786eb4ebd6250c4be7c5e4ab7379c4d97`。两个 package script 的 schema/asset/deterministic ZIP/checksum 检查均通过；APK asset 与 default source archive 字节相同。

[PARTIAL] 3. 构建服务已同步 `de96ec0a` 并完成 release 编译与旋转签名：`operit-release-feat_plugin-interface-de96ec0a.apk`，大小 403,183,432 bytes，SHA-256 `3dccd5083bbf793cadc780075564529c05e7a665ff9d76ad07046546d37b173b`，`BUILD SUCCESSFUL in 15m 26s`。JVM、Compose、WebChat test 命令和设备验收尚未执行。
