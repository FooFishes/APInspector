package work.foofish.apinspector.openapi.parse

import work.foofish.apinspector.openapi.OpenApiParseError

/** 解析内部用于中止整个解析的异常；只在 [work.foofish.apinspector.openapi.OpenApiParser] 中被转换为结果。 */
internal class ParseFailure(
    val error: OpenApiParseError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)
