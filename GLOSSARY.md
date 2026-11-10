# APInspector

一个自用的 Android 工具：导入 OpenAPI 文档，浏览其中的接口，快速发起请求并查看请求详情。

## Language

**API 文档 (Spec)**:
用户导入的一份 OpenAPI 3.0 / 3.1 / 3.2 文档，是 App 中的顶层条目。
_Avoid_: 项目, 工作区, API, Collection

**接口 (Operation)**:
API 文档中定义的一个"HTTP 方法 + 路径"组合，例如 `GET /users/{id}`。
_Avoid_: API, 端点, Endpoint

**当前参数 (Working Params)**:
每个接口自动保存的一份未命名参数值，即用户上次填写的状态。
_Avoid_: 草稿, 缓存参数

**预设 (Preset)**:
绑定在某个接口上的一组命名参数值；加载预设即用其值覆盖当前参数。
_Avoid_: 用例, 示例, Example, 保存的请求

**额外参数 (Extra Parameter)**:
用户为接口追加的、文档未声明的 Header 或 Query 参数；与声明的参数一样属于当前参数和预设。
_Avoid_: 自定义参数, 附加 Header

**文档默认值 (Document Defaults)**:
按 API 文档中的示例、默认值和 schema 推出的参数与 Body 初始值；"重置"即回到它。
_Avoid_: 初始值, 模板

**环境 (Environment)**:
属于某个 API 文档的一组 baseUrl 与变量；同一时间每个 API 文档只有一个当前环境。
_Avoid_: Profile, Server, 配置

**变量 (Variable)**:
在请求里以 `{{name}}` 引用的命名值。定义在环境中时作用于整个 API 文档；定义在临时请求上时只作用于该临时请求。
_Avoid_: 占位符, 参数

**提取规则 (Extraction Rule)**:
一条"从成功响应中按 JSONPath 取值并写入当前环境某个变量"的规则。
_Avoid_: 后置脚本, Test, Post-response script

**请求记录 (History Entry)**:
一次已发出请求的完整快照：实际请求、发送时的当前参数、收到的响应或错误、来源与所用环境。
_Avoid_: 日志, Log, 历史请求

**实际请求 (Sent Request)**:
变量替换、鉴权注入之后真正发出的方法、URL、Header 与 Body。
_Avoid_: 原始请求, Raw request

**重放 (Replay)**:
把某条请求记录的实际请求原样再发一次，不重新替换变量或注入鉴权。
_Avoid_: 重发, 重试, Resend

**自定义请求 (Custom Request)**:
用户手动填写、而非由接口生成的请求；可归属某个 API 文档，也可不归属任何 API 文档。
_Avoid_: 空白请求, 新建请求

**临时请求 (Scratch Request)**:
不归属任何 API 文档的自定义请求；它们集中显示在首页固定置顶的"临时请求"入口中。
_Avoid_: 草稿, 草稿箱, 未分类
