package work.foofish.apinspector.testing

import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.OpenApiParseResult
import work.foofish.apinspector.openapi.OpenApiParser
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.spec.LoadedDocument
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecOrigin
import work.foofish.apinspector.spec.SpecSummary
import java.time.Instant

/** 解析一份 OpenAPI 3.1 文档；[body] 为 `info` 之后的顶层 YAML。 */
fun document(body: String): OpenApiDocument {
    val source =
        buildString {
            appendLine("openapi: 3.1.0")
            appendLine("info:")
            appendLine("  title: Test")
            appendLine("  version: '1'")
            append(body.trimIndent())
        }
    val result = OpenApiParser.parse(source)
    check(result is OpenApiParseResult.Success) { "测试文档无法解析：$result" }
    return result.document
}

fun loaded(body: String): LoadedDocument = LoadedDocument(document(body), warnings = emptyList())

fun OpenApiDocument.operation(key: OperationKey): Operation = operations.single { it.key == key }

fun summary(
    id: String,
    title: String = "Spec $id",
    origin: SpecOrigin = SpecOrigin.Url("https://example.com/$id.yaml"),
    updatedAt: Instant = Instant.EPOCH,
    pinned: Boolean = false,
): SpecSummary = SpecSummary(SpecId(id), title, version = "1", origin, operationCount = 0, pinned, updatedAt)
