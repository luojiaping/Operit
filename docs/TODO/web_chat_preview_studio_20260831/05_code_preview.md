# 05 代码预览模式

原本状况：无。

意图：面向会写 JavaScript 的用户，粘贴 main.js 与 ui 脚本或上传 .toolpkg，
在模拟器对应插槽实时看到渲染结果。

## 作用域

- 粘贴 main.js 与 index.ui.js 源码，或上传 .toolpkg 由浏览器解包读取
- 注册管线在浏览器执行 registerToolPkg 注册声明，render hook 收到与 app 一致
  的 payload 结构
- 渲染结果挂到 04 的插槽注入位
- 首期同源执行用户代码，预览站为本地工具无第三方注入面；沙箱 iframe 隔离
  列为后续增强
- 执行错误显式展示在预览面板，不静默

## 期待的新实现状况

examples/input_slot_demo 直接拖入即可在预览站复现 app 中的插槽效果。
