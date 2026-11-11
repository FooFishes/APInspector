package work.foofish.apinspector.ui.text

/**
 * 状态码的标准原因短语（RFC 9110）。
 *
 * HTTP/2 起响应不再携带原因短语，界面用它补上，让 `404` 旁边始终有一句 `Not Found`。
 */
fun reasonPhrase(code: Int): String? = REASON_PHRASES[code]

private val REASON_PHRASES: Map<Int, String> =
    mapOf(
        100 to "Continue",
        101 to "Switching Protocols",
        200 to "OK",
        201 to "Created",
        202 to "Accepted",
        203 to "Non-Authoritative Information",
        204 to "No Content",
        205 to "Reset Content",
        206 to "Partial Content",
        300 to "Multiple Choices",
        301 to "Moved Permanently",
        302 to "Found",
        303 to "See Other",
        304 to "Not Modified",
        307 to "Temporary Redirect",
        308 to "Permanent Redirect",
        400 to "Bad Request",
        401 to "Unauthorized",
        402 to "Payment Required",
        403 to "Forbidden",
        404 to "Not Found",
        405 to "Method Not Allowed",
        406 to "Not Acceptable",
        407 to "Proxy Authentication Required",
        408 to "Request Timeout",
        409 to "Conflict",
        410 to "Gone",
        411 to "Length Required",
        412 to "Precondition Failed",
        413 to "Content Too Large",
        414 to "URI Too Long",
        415 to "Unsupported Media Type",
        416 to "Range Not Satisfiable",
        417 to "Expectation Failed",
        418 to "I'm a teapot",
        421 to "Misdirected Request",
        422 to "Unprocessable Content",
        425 to "Too Early",
        426 to "Upgrade Required",
        428 to "Precondition Required",
        429 to "Too Many Requests",
        431 to "Request Header Fields Too Large",
        451 to "Unavailable For Legal Reasons",
        500 to "Internal Server Error",
        501 to "Not Implemented",
        502 to "Bad Gateway",
        503 to "Service Unavailable",
        504 to "Gateway Timeout",
        505 to "HTTP Version Not Supported",
    )
