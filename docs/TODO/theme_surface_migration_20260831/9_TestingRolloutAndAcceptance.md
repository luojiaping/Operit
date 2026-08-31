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

[TODO] 第一批 device acceptance。
