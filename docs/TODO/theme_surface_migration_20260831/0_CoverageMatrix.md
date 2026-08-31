# Surface 覆盖矩阵

## 状态定义

- `已渲染`：surface 已由专属 scene 或真实 V2 host 消费。
- `基础 host`：已进入 page 或 host-shell 外框，内部 primitive 仍待迁移。
- `待迁移`：当前仅消费 active Material projection 或尚无 surface dispatcher。
- `固定边界`：不受主题包重绘。

## 主窗口与路由

| Surface | 入口或路由家族 | 当前状态 | 批次 |
| --- | --- | --- | --- |
| `app.shell` | `AppShellSceneHost` | 已渲染 scene | 已有 |
| `app.navigation` | Phone drawer、collapsed tablet drawer | 基础 host，文本项已消费 navigation skin | 1、2、3 |
| `chat.main` | `Screen.AiChat` | 已渲染 scene 与聊天 skin | 已有 |
| `memory.graph_library` | `Screen.MemoryBase` | 基础 host | 1、6 |
| `market.home` | Market、notifications | 基础 host | 1、5 |
| `market.category` | Market category | 基础 host | 1、5 |
| `market.entry_detail` | Entry detail、author | 基础 host | 1、5 |
| `market.publisher_console` | Market manage | 基础 host | 1、5 |
| `market.artifact_editor` | Artifact publish/edit | 基础 host | 1、5 |
| `market.repository_editor` | Repository publish/edit | 基础 host | 1、5 |
| `packages.manager` | Package manager | 基础 host | 1、4 |
| `workflow.library` | Workflow list | 基础 host | 1、6 |
| `workflow.canvas_editor` | Workflow detail | 基础 host | 1、6 |
| `files.browser` | File manager | 基础 host | 1、6 |
| `assistant.profile` | Assistant config | 基础 host | 1、4 |
| `persona.card_studio` | Persona generation、model prompts | 基础 host | 1、4 |
| `prompt_tag.market` | Tag market | 基础 host | 1、4 |
| `settings.index` | Settings | 基础 host | 1、4 |
| `settings.form` | Settings detail routes | 基础 host | 1、4 |
| `settings.statistics` | Token usage | 基础 host | 1、4 |
| `toolbox.index` | Toolbox launcher | 基础 host | 1、6 |
| `toolbox.tool` | Operit tool pages | 基础 host | 1、6 |
| `terminal.shell` | Terminal/setup/autoconfig | 基础 host shell | 1、6、7 |
| `plugin.host_shell` | ToolPkg Compose DSL/config | 基础 host shell | 1、7 |

## 独立宿主与反馈

| Surface | 入口 | 当前状态 | 批次 |
| --- | --- | --- | --- |
| `chat.floating` | Floating chat service | 待迁移 | 7 |
| `chat.permission_overlay` | Permission request overlay | 待迁移 | 7 |
| `browser.shell` | Browser/WebView outer chrome | 待迁移 | 7 |
| `web_chat.main` | WebChat bridge/page | 待迁移 | 7 |
| `media.shell` | Media outer chrome | 待迁移 | 7 |
| `overlay.dialog` | Shared Operit dialogs | 待迁移 | 7 |
| `overlay.sheet` | Shared Operit sheets | 待迁移 | 7 |
| `overlay.menu` | Shared menus | 待迁移 | 7 |
| `overlay.snackbar` | Shared snackbar | 待迁移 | 3、7 |
| `overlay.toast` | Shared toast | 待迁移 | 3、7 |
| `state.loading` | AppContent loading | 已渲染 template 与 status skin | 1、3 |
| `state.empty` | Shared empty state | 待迁移 | 3 |
| `state.error` | Shared error state | 待迁移 | 3 |

## 例外边界

| UI | 状态 | 原因 |
| --- | --- | --- |
| Android system permission dialog、SAF、IME | 固定边界 | 平台拥有视觉与行为 |
| Plugin Compose DSL/WebView/Canvas interior | 固定边界 | 插件拥有内部 UI；只主题化 Operit shell |
| First-run、crash report、data repair、Glance widget | 固定边界 | 恢复和系统可用性界面不依赖可变主题 |

## 维护规则

新增 `Screen` 时必须同时更新 `ScreenRouteRegistry` 绑定表和本矩阵。新增 detached host 时必须登记对应 `ThemeSurfaceCatalogV2` surface、host 类型和迁移批次。
