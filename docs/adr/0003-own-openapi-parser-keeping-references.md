# 自写 OpenAPI 解析器，Schema 引用保留为引用而不内联

`:openapi` 自己实现 OpenAPI 3.0 / 3.1 / 3.2 的解析：JSON 用 kotlinx.serialization，YAML 用 snakeyaml-engine 的节点树（YAML 1.2 Core Schema），统一转成 `JsonElement` 后手工建模，而不是使用 swagger-parser 等现成库。Schema 中的内部 `$ref` 保留为 `Schema.Reference`，目标登记在 `OpenApiDocument.schemas` 中，不做内联展开；参数、响应等其他对象的引用则在解析时直接解析为实际对象。这样循环引用（如树形结构）不会导致无限展开，文档页也能"点击 `$ref` 逐层展开"；同时我们完全控制宽松解析的行为：无法解析的引用、类型不符的字段只在对应位置标记并给出警告，不会让整份文档失败。代价是需要自己维护对规范的覆盖范围，并归一化各版本的差异（`nullable`、类型数组、`exclusiveMinimum` 两种写法、3.2 的 `$self` 与自定义方法等）。

## Considered Options

- **swagger-parser（io.swagger.parser.v3）**：覆盖完整，但依赖 Jackson 等大量库、体积大，默认把引用内联展开并在遇到问题时整体报错或静默修正，不符合"标记未解析的引用、不影响其余部分"的要求。
- **kotlinx.serialization 直接反序列化为 `@Serializable` 模型**：代码少，但 OpenAPI 中 `$ref` 可出现在几乎任何位置、3.0 与 3.1 字段类型不同，用固定的数据类表达会变得别扭，也难以在字段级别给出带位置的警告。
