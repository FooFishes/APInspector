package work.foofish.apinspector.openapi

/**
 * 接口（Operation）：API 文档中的一个"HTTP 方法 + 路径"。
 */
public data class Operation(
    public val key: OperationKey,
    public val operationId: String?,
    public val summary: String?,
    public val description: String?,
    public val tags: List<String>,
    public val deprecated: Boolean,
    /** Path Item 与 Operation 两级参数合并后的结果；同名同位置时以 Operation 级为准。 */
    public val parameters: List<Resolvable<Parameter>>,
    public val requestBody: Resolvable<RequestBody>?,
    /** 以状态码（如 `200`、`4XX`、`default`）为键，保持文档中的顺序。 */
    public val responses: Map<String, Resolvable<Response>>,
    /**
     * 生效的鉴权要求：接口未声明时继承文档顶层的 `security`。
     *
     * 满足其中任意一项即可；为空表示该接口无需鉴权。
     */
    public val security: List<SecurityRequirement>,
)
