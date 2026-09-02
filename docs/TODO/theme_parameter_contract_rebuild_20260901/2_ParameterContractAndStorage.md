# schema 4 参数与资源契约

## 契约

schema 4 使用强类型值：颜色、模式独立颜色、布尔、选项、范围数值、图片 URI、视频 URI、字体 URI、图片布局、边距和圆角。每种 value 只能配对兼容 control 与 effect。

effect 使用封闭 target 目录，覆盖 token、Material、Typography、场景媒体、组件状态、聊天、Composer 和 App Chrome。archive linker 校验参数 owner、target owner、重复写入、范围、资源 MIME 和条件依赖。

## 存储

新的主题实例数据只记录 schema 4 参数值。`theme_package_selection_v4` 在首次创建时写入 bundled default，不读取 schema 3 的实例、URI journal 或选择记录。图片、视频和字体资源的持久 URI 授权由新资源所有权 journal 管理，只服务新实例。

## 最小功能单元

[DONE] 1. 删除 schema 3 value/control/effect 和旧 selection repair，建立 schema 4 模型、序列化及 validator。

[DONE] 2. 建立新主题实例存储与资源授权生命周期。

[PARTIAL] 3. 已添加 value、condition、slider、component owner 与 presentation target unit coverage；测试命令尚未执行。
