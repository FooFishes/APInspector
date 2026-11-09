package work.foofish.apinspector.openapi

/** [OpenApiParser.parse] 的结果。 */
public sealed interface OpenApiParseResult {
    /** 解析成功；[warnings] 列出被忽略或无法解析的部分，不影响其余内容。 */
    public data class Success(
        public val document: OpenApiDocument,
        public val warnings: List<ParseWarning>,
    ) : OpenApiParseResult

    public data class Failure(
        public val error: OpenApiParseError,
    ) : OpenApiParseResult
}

/** 导致整个文档无法使用的错误。 */
public sealed interface OpenApiParseError {
    /** JSON / YAML 语法错误；[detail] 是解析库给出的原始说明。 */
    public data class Syntax(
        public val format: SourceFormat,
        public val detail: String,
        public val position: TextPosition?,
    ) : OpenApiParseError

    /** YAML 映射中出现重复的键。 */
    public data class DuplicateKey(
        public val key: String,
        public val position: TextPosition?,
    ) : OpenApiParseError

    /** YAML 别名引用了包含它自身的节点，无法转换为有限的树。 */
    public data class RecursiveAlias(
        public val position: TextPosition?,
    ) : OpenApiParseError

    /** 文档根节点不是对象。 */
    public data object RootNotObject : OpenApiParseError

    /** 既没有 `openapi` 也没有 `swagger` 字段。 */
    public data object NotOpenApi : OpenApiParseError

    /** Swagger 2.0 文档，明确不支持（PRD 2）。 */
    public data class Swagger2Unsupported(
        public val version: String,
    ) : OpenApiParseError

    /** `openapi` 字段不是 3.0.x、3.1.x 或 3.2.x。 */
    public data class UnsupportedVersion(
        public val version: String,
    ) : OpenApiParseError

    /** 缺少使用文档所必需的字段，[pointer] 是该字段的位置。 */
    public data class MissingRequiredField(
        public val pointer: String,
    ) : OpenApiParseError
}

public enum class SourceFormat {
    JSON,
    YAML,
}

/** 源文本中的位置，行与列均从 1 开始。 */
public data class TextPosition(
    public val line: Int,
    public val column: Int,
)

/**
 * 解析中被忽略的问题。[pointer] 是问题所在位置的 URI 片段，例如 `#/paths/~1users/get/parameters/0`。
 */
public sealed interface ParseWarning {
    public val pointer: String

    /** 字段的 JSON 类型不符，已忽略该字段。 */
    public data class UnexpectedType(
        override val pointer: String,
        public val expected: JsonType,
    ) : ParseWarning

    /** 字段取值不在规范允许的范围内，已忽略。 */
    public data class InvalidValue(
        override val pointer: String,
        public val value: String,
    ) : ParseWarning

    /** 对象缺少必需字段 [field]，已忽略整个对象。 */
    public data class MissingField(
        override val pointer: String,
        public val field: String,
    ) : ParseWarning

    /** `$ref` 无法解析，已在对应位置标记为未解析的引用。 */
    public data class UnresolvedReference(
        override val pointer: String,
        public val ref: String,
        public val reason: UnresolvedReason,
    ) : ParseWarning
}

public enum class JsonType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    INTEGER,
    BOOLEAN,
}
