package work.foofish.apinspector.openapi

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import work.foofish.apinspector.openapi.parse.DocumentReader
import work.foofish.apinspector.openapi.parse.Field
import work.foofish.apinspector.openapi.parse.ParseFailure
import work.foofish.apinspector.openapi.parse.SourceReader

/**
 * 解析 OpenAPI 3.0 / 3.1 / 3.2 文档（JSON 或 YAML）。
 *
 * 解析是宽松的：只有语法错误、版本不受支持或缺少 `info.title` 时才失败；
 * 其余不合规的部分被忽略并记入 [OpenApiParseResult.Success.warnings]。
 * 只解析文档内部的 `$ref`（PRD 2）。
 */
public object OpenApiParser {
    private val SUPPORTED_VERSIONS: Map<OpenApiVersion, Regex> =
        mapOf(
            OpenApiVersion.V3_0 to Regex("""3\.0(\.\d+)?"""),
            OpenApiVersion.V3_1 to Regex("""3\.1(\.\d+)?"""),
            OpenApiVersion.V3_2 to Regex("""3\.2(\.\d+)?"""),
        )

    /** CPU 密集且可能耗时（大型文档），调用方应在后台线程执行。 */
    public fun parse(source: String): OpenApiParseResult =
        try {
            val root = SourceReader.read(source) as? JsonObject ?: throw ParseFailure(OpenApiParseError.RootNotObject)
            val declaredVersion = declaredVersion(root)
            DocumentReader(root, supportedVersion(declaredVersion), declaredVersion).read()
        } catch (failure: ParseFailure) {
            OpenApiParseResult.Failure(failure.error)
        }

    private fun declaredVersion(root: JsonObject): String =
        root.scalar(Field.OPENAPI)
            ?: throw ParseFailure(root.scalar(Field.SWAGGER)?.let(OpenApiParseError::Swagger2Unsupported) ?: OpenApiParseError.NotOpenApi)

    private fun supportedVersion(declared: String): OpenApiVersion =
        SUPPORTED_VERSIONS.entries.firstOrNull { (_, pattern) -> pattern.matches(declared) }?.key
            ?: throw ParseFailure(OpenApiParseError.UnsupportedVersion(declared))

    private fun JsonObject.scalar(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it == JsonNull }?.content
}
