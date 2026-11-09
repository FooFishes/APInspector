package work.foofish.apinspector.openapi

/**
 * 接口（Operation）的身份：HTTP 方法 + 路径模板，例如 `GET /users/{id}`。
 *
 * 刷新 API 文档时以它匹配新旧接口，而不是 operationId（见 ADR-0001）。
 */
public data class OperationKey(
    public val method: HttpMethod,
    public val path: String,
) {
    init {
        require(path.startsWith(PATH_PREFIX)) { "接口路径必须以 \"$PATH_PREFIX\" 开头：$path" }
    }

    override fun toString(): String = "$method $path"

    private companion object {
        /** OpenAPI 规定 Paths Object 的字段名必须以 `/` 开头。 */
        const val PATH_PREFIX = "/"
    }
}
