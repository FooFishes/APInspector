package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonElement

/**
 * Parameter Object。路径参数的 [required] 恒为 `true`。
 */
public data class Parameter(
    public val name: String,
    public val location: ParameterLocation,
    public val description: String?,
    public val required: Boolean,
    public val deprecated: Boolean,
    /** 以 [schema] 描述的参数如何序列化；以 [content] 描述的参数（包括 `querystring`）为 `null`。 */
    public val serialization: ParameterSerialization?,
    public val schema: Schema?,
    public val content: Map<String, Resolvable<MediaTypeContent>>,
    public val example: JsonElement?,
    public val examples: Map<String, Resolvable<Example>>,
)

public enum class ParameterLocation {
    PATH,
    QUERY,

    /** OpenAPI 3.2：整个查询串作为一个值，由 `content` 描述其格式。 */
    QUERY_STRING,
    HEADER,
    COOKIE,
}

/** OpenAPI `style` 与 `explode`，已按规范补全默认值。 */
public data class ParameterSerialization(
    public val style: ParameterStyle,
    public val explode: Boolean,
)

/** 参数序列化方式（OpenAPI `style`）。 */
public enum class ParameterStyle {
    MATRIX,
    LABEL,
    FORM,
    SIMPLE,
    SPACE_DELIMITED,
    PIPE_DELIMITED,
    DEEP_OBJECT,

    /** OpenAPI 3.2：按 RFC 6265 的 `Cookie` 语法序列化。 */
    COOKIE,
}
