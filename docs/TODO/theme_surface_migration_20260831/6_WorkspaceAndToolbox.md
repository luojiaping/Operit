# 工作区与工具箱

## 目标 surface

- `memory.graph_library`
- `workflow.library`
- `workflow.canvas_editor`
- `files.browser`
- `toolbox.index`
- `toolbox.tool`

## 页面范围

- Memory graph library
- Workflow list 与 canvas editor
- File manager
- Toolbox launcher 与 Operit 自有工具页

## 介入方式

页面根使用非裁切 surface template，工具栏、列表、FAB、path bar、选中状态、状态条和 dialog 使用 V2 primitive。Graph 与 workflow canvas 保留自己的坐标、缩放、拖拽和渲染循环；只改变 Operit chrome、面板、工具条和弹层。

## 风险与约束

- 不让 frame 或 scene wrapper 截获 canvas 手势。
- 文件路径、选择模式、上下文菜单和拖拽区域保持稳定尺寸。
- 工具页可能含 web/canvas/native 内容时，只主题化 Operit 外壳。

## 验收

- canvas/graph 不被 page frame 裁切，拖动缩放连续。
- file list、工具格、workflow card 和操作栏随主题切换。
- loading、empty、permission 和错误路径使用通用状态层。

## 进展

[TODO] Memory/workflow 迁移。

[TODO] Files/toolbox 迁移。
