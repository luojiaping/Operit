# 03 SimulatorShell 与多入口构建、docker 部署

原本状况：web-chat 单入口，仅作为真机前端的静态资源打进 APK。

意图：抽出模拟器外壳组件，新增预览站构建入口，复用同一套聊天组件；预览站
部署在本机 docker。

## 作用域

- SimulatorShell：设备宽度、明暗、聊天风格 cursor 与 bubble、输入风格 classic
  与 agent 的切换控件；去掉 980px 限宽改为宽度参数
- vite 多入口：index.html 现有真机前端不动，新增 preview 入口
- Dockerfile 用 nginx 托管预览站构建产物，compose 文件本机部署，端口避开
  8094 真机服务
- 真机入口的 sync-to-android-assets 流程不受影响

## 期待的新实现状况

npm run build 产出双入口；docker compose up 后预览站在本机可访问且 mock 模式
完整可用。
