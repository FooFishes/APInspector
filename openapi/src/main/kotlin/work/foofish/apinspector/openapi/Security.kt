package work.foofish.apinspector.openapi

/** Security Scheme Object。 */
public sealed interface SecurityScheme {
    public val description: String?

    /** OpenAPI 3.2 新增：方案已弃用。 */
    public val deprecated: Boolean

    /** `type: apiKey`。 */
    public data class ApiKey(
        public val name: String,
        public val location: ApiKeyLocation,
        override val description: String?,
        override val deprecated: Boolean,
    ) : SecurityScheme

    /** `type: http`，例如 Bearer 或 Basic。 */
    public data class Http(
        /** HTTP 认证方案名，已转为小写（RFC 7235 规定不区分大小写），例如 `bearer`、`basic`。 */
        public val scheme: String,
        public val bearerFormat: String?,
        override val description: String?,
        override val deprecated: Boolean,
    ) : SecurityScheme

    /** `oauth2`、`openIdConnect`、`mutualTLS` 等不自动处理的方案（PRD 5）。 */
    public data class Other(
        public val type: String,
        override val description: String?,
        override val deprecated: Boolean,
    ) : SecurityScheme
}

public enum class ApiKeyLocation {
    HEADER,
    QUERY,
    COOKIE,
}

/**
 * Security Requirement Object：其中列出的方案需要同时满足。
 *
 * 空的要求（`{}`）表示该接口也可以不鉴权。
 */
public data class SecurityRequirement(
    /** 方案名 → 所需 scope。 */
    public val schemes: Map<String, List<String>>,
)
