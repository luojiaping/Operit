# 06 低代码模板与 .toolpkg 导出

原本状况：无。普通用户制作 .toolpkg 需要完整工具链。

意图：填表单选模板生成受限但可直接导入 app 的 .toolpkg。

## 作用域

- 模板库首期：快捷按钮排、状态卡、记事板，全部只使用 04 白名单组件
- 表单项：slot 选择、文案、配色、按钮数量与动作标签
- 生成物：manifest.json、main.js、dist/ui/*/index.ui.js，结构与
  examples/input_slot_demo 对齐
- ZIP 用 store 方式在浏览器本地打包，下载 .toolpkg
- manifest 字段经与 ToolPkgMainRegistrationScriptParser 相同的白名单校验

## 期待的新实现状况

生成的包被 app PackageManager 导入后，三个 slot 渲染结果与预览站一致。
