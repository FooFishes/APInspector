package work.foofish.apinspector.openapi

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail

internal fun parseSuccess(source: String): OpenApiParseResult.Success =
    assertInstanceOf(OpenApiParseResult.Success::class.java, OpenApiParser.parse(source))

internal fun parseFailure(source: String): OpenApiParseError =
    assertInstanceOf(OpenApiParseResult.Failure::class.java, OpenApiParser.parse(source)).error

/** 最小的合法文档头，后接 [body]（YAML，顶层缩进为 0）。 */
internal fun spec(
    body: String,
    version: String = "3.1.0",
): String =
    buildString {
        appendLine("openapi: $version")
        appendLine("info:")
        appendLine("  title: Test")
        appendLine("  version: '1'")
        append(body.trimIndent())
    }

internal fun fixture(name: String): String =
    checkNotNull(OpenApiParser::class.java.getResource("/fixtures/$name")) { "缺少测试夹具 $name" }.readText()

internal fun OpenApiDocument.operation(
    method: HttpMethod,
    path: String,
): Operation = operations.single { it.key == OperationKey(method, path) }

internal fun <T> Resolvable<T>.resolved(): T = (this as? Resolvable.Resolved<T>)?.value ?: fail("预期已解析，实际为 $this")
