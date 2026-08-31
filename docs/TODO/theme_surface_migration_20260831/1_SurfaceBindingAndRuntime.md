# Surface 绑定与运行时

## 旧实现

`ThemeSurfaceCatalogV2` 列出 37 个 stable surface ID，theme linker 也要求包覆盖全部 ID，但 `Screen`、`RouteSpec` 与动态路由没有 surface 字段。`ThemePackageSceneRuntimeV2.sceneFor()` 仅接受 `SCENE`，因此 `TEMPLATE` 与 `HOST_SHELL` 没有实际 renderer。

这造成“包声明覆盖”与“真实 UI 已主题化”不一致，无法从代码或测试证明每个 surface 都被消费。

## 目标实现

1. 定义原生 route 到 `ThemeSurfaceIdV2` 的单一绑定表，不根据 Kotlin 类名或显示标题推导主题 API。
2. 为 `Screen` 和 native route registry 提供 surface 解析入口；动态 ToolPkg route 解析为 `Screen` 后明确绑定 `plugin.host_shell`。
3. 创建 `ThemeSurfaceHostV2`：
   - `SCENE` 使用场景宿主与已登记 slot。
   - `TEMPLATE` 使用 package-owned page template，承载原生内容。
   - `HOST_SHELL` 使用 package-owned outer shell，承载外部或插件内容。
4. 每个 host 在读取 implementation 前校验对应 surface 已由 active runtime 链接。

## 绑定清单

| UI 家族 | Surface |
| --- | --- |
| 主应用壳 | `app.shell` |
| 抽屉与平板导航 | `app.navigation` |
| 主聊天 | `chat.main` |
| 悬浮聊天 | `chat.floating` |
| 权限外壳 | `chat.permission_overlay` |
| 浏览器外壳 | `browser.shell` |
| WebChat | `web_chat.main` |
| 设置页 | `settings.index`、`settings.form`、`settings.statistics` |
| 包与市场 | `packages.manager`、`market.*` |
| 记忆、工作流、文件、工具 | `memory.graph_library`、`workflow.*`、`files.browser`、`toolbox.*` |
| 弹层与反馈 | `overlay.*`、`state.*` |
| 外部内容外壳 | `terminal.shell`、`media.shell`、`plugin.host_shell` |

## 入口

- `ui/main/navigation/AppNavigationModels.kt`
- `ui/main/screens/OperitScreens.kt`
- `ui/main/components/AppContent.kt`
- `ui/theme/ThemePackageSceneRuntimeV2.kt`
- 新增 `ui/theme/ThemeSurfaceHostV2.kt`

## 最小提交单元

1. route/screen surface 绑定模型与覆盖测试。
2. `TEMPLATE` 和 `HOST_SHELL` renderer。
3. `AppContent` route content 接入 page host。
4. 动态插件 route 外壳接入 `plugin.host_shell`。

## 验收

- 每个原生 `Screen` 和每个动态 host route 都能解析到明确 surface。
- `SCENE`、`TEMPLATE`、`HOST_SHELL` 各有至少一个真实生产调用点。
- route surface 与 package implementation kind 不匹配时，测试必须失败。
- 主题切换不重建业务 ViewModel、导航栈或滚动状态。

## 进展

[DONE] `ScreenRouteRegistry` 已建立 native `Screen` class 到 stable `ThemeSurfaceIdV2` 的完整绑定表，并在初始化时拒绝缺失或过期绑定。

[DONE] 动态 ToolPkg route 解析为 `Screen` 后固定绑定 `plugin.host_shell`，不向通用导航模型泄漏主题运行时类型。

[DONE] `ThemeSurfaceHostV2` 已为 `TEMPLATE` 提供 page renderer、为 `HOST_SHELL` 提供 section 外框；`SCENE` 继续只能由专属 scene host 消费。

[DONE] `ThemeSurfaceHostPolicyV2` 已锁定 host 支持的 implementation kind：`app.shell`/`chat.main` 为 scene，browser/terminal/media/plugin 为 host shell，其余日常 surface 为 template。archive validator 与 linker 都拒绝不匹配声明。

[DONE] runtime index 独立链接每个已安装包；不兼容的外部包不会阻止内置默认包或其他有效包进入索引。启动选择校验只接受已链接坐标，主题设置页也拒绝激活不可链接包。

[DONE] scene surface 必须引用同名注册 scene；链接器保留 inherited asset kind 并校验 image、nine-slice、path 与 font 的实际类型，阻止错误资源进入 renderer。

[DONE] `AppContent` 已将所有非聊天 native route 与 ToolPkg content 包进对应 surface host；`chat.main` 保留专属 `ChatMainSceneHost`。

[DONE] 新增 `ScreenThemeSurfaceBindingV2Test`，覆盖 native route 绑定完整性和代表性 route。

[TODO] 增加 Compose cache/主题切换测试，确认 keep-alive route 在 template host 中保持状态。
