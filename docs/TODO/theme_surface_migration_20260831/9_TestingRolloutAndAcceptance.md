# 测试、发布与验收

## 自动门槛

1. route/screen 到 `ThemeSurfaceIdV2` 的覆盖测试。
2. `SCENE`、`TEMPLATE`、`HOST_SHELL` implementation kind 的 host 测试。
3. 每个 V2 primitive 的 skin state、content color、frame 和语义测试。
4. page、navigation、overlay、loading/empty/error 的 Compose 像素测试。
5. default archive 与 cyber basis 的严格归档/linker 测试。
6. 不兼容已安装包不阻塞默认主题启动、不能被激活且会显示为 disabled 的回归测试。
7. scene surface 的同名 scene 绑定与 inherited asset kind 的 linker 回归测试。

## 设备矩阵

| 场景 | 必查项目 |
| --- | --- |
| 手机竖屏 | 抽屉、app bar、page frame、IME、dialog/sheet/menu |
| 平板 | rail、双栏、sidebar 收放、route 转场 |
| 聊天 | Agent/Classic、Cursor/Bubble、长消息、流式消息、浮动窗 |
| 设置与市场 | 长表单、lazy list、tab、选择态、destructive 操作 |
| 工作区 | graph/canvas 手势、文件菜单、toolbox grid |
| 外部宿主 | 权限 overlay、browser、terminal、media、plugin shell |

## 发布顺序

每一批按以下顺序完成：

1. 更新该批 TODO 文档和覆盖矩阵。
2. 完成应用代码与 focused tests。
3. 静态审查 route/surface/skin 覆盖。
4. 用户明确要求后打包主题归档、同步默认包、构建 release APK。
5. 记录 APK、theme archive SHA-256 和真机截图结论。

## 完成定义

- 37 个 required daily surface 都有真实 runtime consumer。
- 16 个 component skin 都至少有一个生产调用点，状态 skin 也有明确交互路径。
- Operit 自有日常界面在 default/cyber 主题下均可读、可操作、无重叠、无裁切。
- 外部内容与系统 UI 的所有权边界未被侵入。

## 进展

[PARTIAL] 第一批 automated tests 已新增，尚未执行命令。

[DONE] 第一批已新增 route binding、surface host policy、archive kind rejection、linker kind rejection 与不兼容已安装包隔离测试；尚未执行测试命令。

[DONE] 构建服务已同步 `194f5671` 并完成 release 编译：`operit-release-feat_plugin-interface-194f5671.apk`，SHA-256 `024f09739ab9c2a286de7eb3201a43c104be30fe81baa213312708836370f568`。本次只执行 release build，未执行 JVM/Android test。

[DONE] Overlay primitive 试点已新增 surface-to-component unit test、dialog skin container/frame/content 与 child click semantics 的 Compose test、ChatToast close callback test，以及 WebSession prompt input/confirm callback test；尚未执行测试命令。sheet scrim/IME 留作 device acceptance。

[DONE] 构建服务已同步 `8d1322af` 并完成 release 编译：`operit-release-feat_plugin-interface-8d1322af.apk`，SHA-256 `249608305136aa6989a8370c42967fce7964d7b0ba1e84c14e3ef75fc621f39d`。本次未执行 JVM/Android test。

[DONE] Input primitive 试点已新增 state priority、archive/linker interaction-state contract、input frame/content/disabled semantics、Memory IME search callback、WebSession prompt 与 File Manager search confirm callback test；尚未执行测试命令。

[DONE] 构建服务已同步 `ed980b5f` 并完成 release 编译：`operit-release-feat_plugin-interface-ed980b5f.apk`，SHA-256 `7eef2a88ab0bb75c4d37730de791789c86b7bf0f9749a9d6ccf4b6f930902e19`。本次未执行 JVM/Android test。

[TODO] 第一批 device acceptance。
