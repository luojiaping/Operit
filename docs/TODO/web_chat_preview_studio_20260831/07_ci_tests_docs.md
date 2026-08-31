# 07 CI、测试与文档收尾

原本状况：web-chat 仅 typecheck 与 pr_check 的 legacy 检测，无测试。

意图：为预览器引入针对性测试并接入现有 CI 路径。

## 作用域

- vitest：Transport mock 与 DSL 渲染器单测，fixtures 驱动
- pr-check.yml 的 web 变更检测覆盖 preview 入口
- docs/doc-src 增预览器架构文档：入口结构、Transport、DSL 渲染器能力边界
- feature-protocol/external_http_chat.md 的快照字段表补充 web 侧可选字段
  说明，注明 app 侧下发为跟进项
- TODO 文件夹按规范收尾

## 期待的新实现状况

CI 在 web-chat 变更时跑 typecheck 与 vitest；文档反映预览器真实结构。
