package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonElement

/** Request Body Object。 */
public data class RequestBody(
    public val description: String?,
    public val required: Boolean,
    /** 以 content type 为键，保持文档中的顺序。 */
    public val content: Map<String, Resolvable<MediaTypeContent>>,
)

/** Response Object。 */
public data class Response(
    /** OpenAPI 3.2 新增的简短说明。 */
    public val summary: String?,
    public val description: String?,
    public val headers: Map<String, Resolvable<Header>>,
    /** 以 content type 为键，保持文档中的顺序。 */
    public val content: Map<String, Resolvable<MediaTypeContent>>,
)

/** Header Object（响应头）。 */
public data class Header(
    public val description: String?,
    public val required: Boolean,
    public val deprecated: Boolean,
    public val schema: Schema?,
    public val content: Map<String, Resolvable<MediaTypeContent>>,
)

/**
 * Media Type Object：某个 content type 下的 Schema 与示例。OpenAPI 3.2 起可以用 `$ref` 引用。
 */
public data class MediaTypeContent(
    public val schema: Schema?,
    /** OpenAPI 3.2：流式内容（如 `text/event-stream`、`application/jsonl`）中每一项的 Schema。 */
    public val itemSchema: Schema?,
    public val example: JsonElement?,
    public val examples: Map<String, Resolvable<Example>>,
)

/** Example Object。 */
public data class Example(
    public val summary: String?,
    public val description: String?,
    /** OpenAPI 3.2：符合 Schema 的数据形式的示例。 */
    public val dataValue: JsonElement?,
    /** OpenAPI 3.2：序列化后（已编码、转义）的示例文本。 */
    public val serializedValue: String?,
    public val value: JsonElement?,
    /** 指向外部示例的地址；不会被拉取。 */
    public val externalValue: String?,
)
