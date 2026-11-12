# 更新日志

本文件记录每个版本中值得注意的变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [0.1.0] - 未发布

首个版本。

### 新增

- 导入 OpenAPI 3.0 / 3.1 / 3.2 文档（JSON 与 YAML），来源为本地文件、URL 或粘贴的文本；通过 URL 导入的文档可以刷新并查看变更摘要。
- 按 tag 分组的接口列表，支持搜索与收藏；接口文档页展示参数、请求体与响应的 schema 树。
- 发送请求：按 schema 生成的参数与 Body 编辑器，每个接口的当前参数与多组预设。
- 环境、变量与鉴权（Bearer、Basic、API Key）；每个环境独立的 Cookie、代理、超时与证书校验开关。
- 请求记录与响应详情：耗时分解、JSON 折叠树、重放、恢复参数、复制为 cURL。
- 自定义请求与临时请求，支持从 cURL 导入。
- 提取规则：按 JSONPath 把响应中的值写入变量。
- 备份的导出与导入，可设置密码加密。
- Material 3 Expressive 界面，支持动态取色与深色模式，适配手机、折叠屏和平板。

[0.1.0]: https://github.com/FooFishes/APInspector/commits/main
