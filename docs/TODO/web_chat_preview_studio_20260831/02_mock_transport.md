# 02 transport 抽象与 MockTransport

原本状况：chatApi.ts 直接 fetch 真机 /api/web/*，没有接口抽象，无后端时页面
停在 Token 连接页，无法独立运行也无法测试。

意图：把网络访问收敛为 Transport 接口，提供 RealTransport 与 MockTransport 双
实现；mock 覆盖启动到可交互所需的全部端点与 SSE 流。

## 作用域

- chatApi.ts 现有 fetch 逻辑收进 RealTransport，导出 Transport 接口
- MockTransport 覆盖：bootstrap、character-selector、model-selector、
  input-settings、memory-selector、chats 增删改查与 select、theme、messages
  分页、uploads、assets、messages/stream 的 SSE
- SSE 用 ReadableStream 按 assistant_delta、user_message、assistant_done 顺序
  发块，块格式与 parseSseBlock 的解析一致
- fixtures：多套主题快照含 01 文档全部新字段；消息集含 text、xml、group、
  file-diff CDATA、tool、status、variants 样例
- 入口用 ?mock=1 进入 mock 模式，跳过 Token 连接页

## 期待的新实现状况

vite dev 无后端可跑完整交互；Transport 接口让 ChatViewModel 不感知数据来源；
fixtures 同时充当渲染回归的黄金样例。
