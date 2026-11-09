package work.foofish.apinspector.openapi

/**
 * 解析后的 OpenAPI 文档内容。
 */
public data class OpenApiDocument(
    public val version: OpenApiVersion,
    /** 文档声明的完整版本号，例如 `3.1.0`。 */
    public val declaredVersion: String,
    public val info: Info,
    /** 文档未声明 `servers` 时，按规范默认为一个 URL 为 `/` 的服务器。 */
    public val servers: List<Server>,
    public val tags: List<Tag>,
    public val operations: List<Operation>,
    public val securitySchemes: Map<String, Resolvable<SecurityScheme>>,
    /** 所有 [Schema.Reference] 的目标，以 [Schema.Reference.ref] 为键；每个引用都必定有对应条目。 */
    public val schemas: Map<String, Schema>,
) {
    /**
     * 沿引用链找到 [schema] 实际指向的 Schema；不是引用时原样返回。
     *
     * 引用链首尾相接时返回 [UnresolvedReason.CIRCULAR]。
     */
    public fun dereference(schema: Schema): Schema {
        val visited = mutableSetOf<String>()
        var current = schema
        while (current is Schema.Reference) {
            if (!visited.add(current.ref)) return Schema.Unresolved(current.ref, UnresolvedReason.CIRCULAR)
            current = schemas.getValue(current.ref)
        }
        return current
    }
}

/** 按发布顺序排列，可以比较先后。 */
public enum class OpenApiVersion {
    V3_0,
    V3_1,
    V3_2,
}

/** Info Object。 */
public data class Info(
    public val title: String,
    public val version: String?,
    public val summary: String?,
    public val description: String?,
)

/** Server Object；导入时每个服务器生成一个环境（PRD 5）。 */
public data class Server(
    /** 可能含有 `{variable}` 模板，也可能是相对地址。 */
    public val url: String,
    /** OpenAPI 3.2 新增的服务器名称。 */
    public val name: String?,
    public val description: String?,
    public val variables: Map<String, ServerVariable>,
)

/** Server Variable Object。 */
public data class ServerVariable(
    public val defaultValue: String,
    public val enumValues: List<String>,
    public val description: String?,
)

/** Tag Object：接口列表按 tag 分组时提供顺序与说明（PRD 3）。 */
public data class Tag(
    public val name: String,
    /** OpenAPI 3.2 新增的简短说明，用于显示。 */
    public val summary: String?,
    public val description: String?,
    /** OpenAPI 3.2：上级 tag 的名称，用于嵌套分组。 */
    public val parent: String?,
    /** OpenAPI 3.2：tag 的类别，例如 `nav`、`badge`、`audience`。 */
    public val kind: String?,
)
