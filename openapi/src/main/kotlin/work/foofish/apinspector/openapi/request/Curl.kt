package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.HttpMethod

/** 把实际请求导出为 cURL 命令（PRD 7.2"复制为 cURL"），参数以单引号括起，适用于 POSIX shell。 */
public object Curl {
    public fun export(request: HttpRequest): String {
        val arguments = mutableListOf(COMMAND)
        if (request.method != HttpMethod.GET || request.body != null) arguments += "-X ${quote(request.method.name)}"
        arguments += quote(request.url)
        request.headers.forEach { arguments += "-H ${quote("${it.name}: ${it.value}")}" }
        when (val body = request.body) {
            null -> {}

            is HttpBody.Text -> {
                arguments += "-H ${quote("$CONTENT_TYPE: ${body.contentType}")}"
                arguments += "--data-raw ${quote(body.text)}"
            }

            is HttpBody.File -> {
                arguments += "-H ${quote("$CONTENT_TYPE: ${body.contentType}")}"
                arguments += "--data-binary ${quote("@" + body.file.name)}"
            }

            is HttpBody.Multipart -> {
                body.parts.forEach { part ->
                    arguments +=
                        when (part) {
                            is HttpPart.Text -> "-F ${quote("${part.name}=${part.value}")}"
                            is HttpPart.File -> "-F ${quote("${part.name}=@\"${part.file.name}\"")}"
                        }
                }
            }
        }
        return arguments.joinToString(LINE_CONTINUATION)
    }

    /**
     * 解析粘贴的 cURL 命令（PRD 4.2"cURL 导入"）：支持 `-X -H -d/--data-raw -F -u --compressed` 等常用参数，
     * 未识别的参数忽略并记入 [CurlImport.ignored]。
     */
    public fun parse(command: String): CurlParse = CurlParser.parse(command)

    private fun quote(value: String): String = "'" + value.replace("'", """'\''""") + "'"

    private const val COMMAND = "curl"
    private const val CONTENT_TYPE = "Content-Type"
    private const val LINE_CONTINUATION = " \\\n  "
}
