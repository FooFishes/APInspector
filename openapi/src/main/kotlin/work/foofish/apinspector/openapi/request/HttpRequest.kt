package work.foofish.apinspector.openapi.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.HttpMethod

/**
 * 一个具体的 HTTP 请求：变量已替换、鉴权已注入。请求记录中的实际请求（PRD 7.1）也以它保存，重放时原样发送。
 *
 * @property headers 按发送顺序；名称保持原样，同名 Header 可以出现多次。不含由传输层生成的 Header
 * （`Host`、`Content-Length`、`Accept-Encoding` 等，见 [isTransportHeader]）与 `Content-Type`（由 [body] 决定）。
 */
@Serializable
public data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: List<Field>,
    val body: HttpBody?,
)

@Serializable
public sealed interface HttpBody {
    /** JSON、文本与已编码的表单。 */
    @Serializable
    @SerialName("text")
    public data class Text(
        val contentType: String,
        val text: String,
    ) : HttpBody

    /** [contentType] 不含 boundary，发送时生成。 */
    @Serializable
    @SerialName("multipart")
    public data class Multipart(
        val contentType: String,
        val parts: List<HttpPart>,
    ) : HttpBody

    @Serializable
    @SerialName("file")
    public data class File(
        val contentType: String,
        val file: FileRef,
    ) : HttpBody
}

@Serializable
public sealed interface HttpPart {
    public val name: String

    @Serializable
    @SerialName("text")
    public data class Text(
        override val name: String,
        val value: String,
    ) : HttpPart

    @Serializable
    @SerialName("file")
    public data class File(
        override val name: String,
        val file: FileRef,
    ) : HttpPart
}
