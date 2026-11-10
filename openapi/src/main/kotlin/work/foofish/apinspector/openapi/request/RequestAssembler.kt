package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.ApiKeyLocation
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.Resolvable
import java.util.Base64

/** 发送时使用的环境内容（PRD 5）。 */
public data class RequestEnvironment(
    /** 可能引用变量。 */
    val baseUrl: String,
    val variables: Map<String, String>,
    /** 方案名 → 凭证。 */
    val credentials: Map<String, Credential>,
)

public sealed interface Assembly {
    /**
     * @property cookies 参数与鉴权给出的 Cookie，发送时与 Cookie 罐合并，同名时以它们为准（PRD 6）。
     */
    public data class Ready(
        val request: HttpRequest,
        val cookies: List<Field>,
    ) : Assembly

    public data class Blocked(
        val problems: List<AssemblyProblem>,
    ) : Assembly
}

/** 阻止发送的原因。 */
public sealed interface AssemblyProblem {
    /** 引用的变量未定义或为空，按首次出现的顺序。 */
    public data class MissingVariables(
        val names: List<String>,
    ) : AssemblyProblem

    /** 替换变量后的 baseUrl 不是 http / https 的绝对地址。 */
    public data class IncompleteBaseUrl(
        val baseUrl: String,
    ) : AssemblyProblem

    /** 文件尚未选择：[part] 为 multipart 字段名；以文件作为整个 Body 时为 `null`。 */
    public data class FileNotSelected(
        val part: String?,
    ) : AssemblyProblem
}

/**
 * 由接口、当前参数与环境组装实际请求（PRD 4、5）。
 *
 * Header 的优先级：手动填写（参数与额外参数）> 鉴权 > 自动附加（`Accept`、`User-Agent`），按名称不区分大小写覆盖。
 */
public class RequestAssembler(
    private val document: OpenApiDocument,
    private val userAgent: String,
) {
    public fun assemble(
        operation: Operation,
        input: RequestInput,
        environment: RequestEnvironment,
    ): Assembly {
        val missing = LinkedHashSet<String>()
        val resolve = { text: String -> Variables.substitute(text, environment.variables, missing) }
        val problems = mutableListOf<AssemblyProblem>()

        val baseUrl = resolve(environment.baseUrl).trimEnd('/')
        if (!isAbsoluteHttpUrl(baseUrl)) problems += AssemblyProblem.IncompleteBaseUrl(baseUrl)

        val manual = manualParts(operation, input, resolve)
        val auth = authParts(operation, environment.credentials, resolve)

        val manualContentType = manual.headers.lastOrNull { it.name.equals(CONTENT_TYPE, ignoreCase = true) }?.value
        val body = input.body?.let { assembleBody(it, manualContentType, resolve, problems) }

        if (missing.isNotEmpty()) problems.add(0, AssemblyProblem.MissingVariables(missing.toList()))
        return if (problems.isEmpty()) ready(operation, baseUrl, manual, auth, body) else Assembly.Blocked(problems)
    }

    private fun manualParts(
        operation: Operation,
        input: RequestInput,
        resolve: (String) -> String,
    ): ManualParts {
        val manual = ManualParts(operation.key.path)
        operation.editableParameters.forEach { parameter ->
            val value = input.parameter(parameter.location, parameter.name) ?: parameter.defaultInput(document)
            if (parameter.isSent(value)) manual.add(parameter.name, parameter.serialize(value.value.map(resolve)))
        }
        input.extras.filter { it.enabled && it.name.isNotBlank() }.forEach { extra ->
            val name = resolve(extra.name)
            val value = resolve(extra.value)
            when (extra.location) {
                ExtraLocation.QUERY -> manual.query += PercentEncoding.encode(name) + "=" + PercentEncoding.encode(value)
                ExtraLocation.HEADER -> manual.headers += Field(name, value)
            }
        }
        return manual
    }

    private fun authParts(
        operation: Operation,
        credentials: Map<String, Credential>,
        resolve: (String) -> String,
    ): AuthParts {
        val schemes =
            document.securitySchemes
                .mapNotNull { (name, scheme) ->
                    (scheme as? Resolvable.Resolved)?.value?.let { name to it }
                }.toMap()
        return AuthParts().apply { selectCredentials(operation.security, schemes, credentials).forEach { add(it, resolve) } }
    }

    /** 合并各部分：手动填写的 Query 与 Cookie 覆盖鉴权的同名项。 */
    private fun ready(
        operation: Operation,
        baseUrl: String,
        manual: ManualParts,
        auth: AuthParts,
        body: HttpBody?,
    ): Assembly.Ready {
        val headers =
            mergeHeaders(
                automatic = listOf(Field(ACCEPT, operation.acceptHeader()), Field(USER_AGENT, userAgent)),
                auth = auth.headers,
                manual = manual.headers.filterNot { body != null && it.name.equals(CONTENT_TYPE, ignoreCase = true) },
            )
        val manualQueryNames = manual.query.map { it.substringBefore('=') }.toSet()
        val query = listOfNotNull(manual.queryString) + manual.query + auth.query.filterNot { it.substringBefore('=') in manualQueryNames }
        val url = baseUrl + manual.path + if (query.isEmpty()) "" else "?" + query.joinToString("&")
        val manualCookieNames = manual.cookies.map { it.name }.toSet()
        val cookies = manual.cookies + auth.cookies.filterNot { it.name in manualCookieNames }
        return Assembly.Ready(HttpRequest(operation.key.method, url, headers, body), cookies)
    }

    private fun assembleBody(
        input: BodyInput,
        manualContentType: String?,
        resolve: (String) -> String,
        problems: MutableList<AssemblyProblem>,
    ): HttpBody? {
        fun contentType(fallback: String): String = manualContentType ?: input.contentType.takeUnless(MediaType::isWildcard) ?: fallback
        return when (input) {
            is BodyInput.Json -> {
                HttpBody.Text(contentType(JSON), resolve(input.text))
            }

            is BodyInput.Text -> {
                HttpBody.Text(contentType(MediaType.TEXT_PLAIN), resolve(input.text))
            }

            is BodyInput.Form -> {
                HttpBody.Text(
                    contentType(MediaType.FORM_URLENCODED),
                    FormText.encode(input.fields.map { Field(resolve(it.name), resolve(it.value)) }),
                )
            }

            is BodyInput.Binary -> {
                input.file?.let { HttpBody.File(contentType(it.mediaType ?: MediaType.OCTET_STREAM), it) }
                    ?: null.also { problems += AssemblyProblem.FileNotSelected(part = null) }
            }

            is BodyInput.Multipart -> {
                HttpBody.Multipart(MediaType.essence(contentType(MediaType.MULTIPART_FORM_DATA)), multipartParts(input, resolve, problems))
            }
        }
    }

    /** 未选择文件的字段记为问题并跳过。 */
    private fun multipartParts(
        input: BodyInput.Multipart,
        resolve: (String) -> String,
        problems: MutableList<AssemblyProblem>,
    ): List<HttpPart> =
        input.parts.mapNotNull { part ->
            val name = resolve(part.name)
            when (val value = part.value) {
                is PartValue.Text -> {
                    HttpPart.Text(name, resolve(value.text))
                }

                is PartValue.File -> {
                    value.file?.let { HttpPart.File(name, it) }
                        ?: null.also { problems += AssemblyProblem.FileNotSelected(part.name) }
                }
            }
        }

    /** 路径、查询、Header 与 Cookie 中手动填写的部分。 */
    private class ManualParts(
        var path: String,
    ) {
        val query = mutableListOf<String>()
        var queryString: String? = null
        val headers = mutableListOf<Field>()
        val cookies = mutableListOf<Field>()

        fun add(
            name: String,
            serialized: SerializedParameter,
        ) {
            when (serialized) {
                is SerializedParameter.Path -> path = path.replace("{$name}", serialized.text)
                is SerializedParameter.Query -> query += serialized.pairs
                is SerializedParameter.QueryString -> queryString = serialized.text
                is SerializedParameter.Header -> headers += Field(name, serialized.value)
                is SerializedParameter.Cookies -> cookies += serialized.pairs
            }
        }
    }

    private class AuthParts {
        val headers = mutableListOf<Field>()
        val query = mutableListOf<String>()
        val cookies = mutableListOf<Field>()

        fun add(
            credential: Credential,
            resolve: (String) -> String,
        ) {
            when (credential) {
                is Credential.Bearer -> {
                    headers += Field(AUTHORIZATION, "Bearer " + resolve(credential.token))
                }

                is Credential.Basic -> {
                    val pair = resolve(credential.username) + ":" + resolve(credential.password)
                    headers += Field(AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(pair.encodeToByteArray()))
                }

                is Credential.ApiKey -> {
                    val name = resolve(credential.name)
                    val value = resolve(credential.value)
                    when (credential.location) {
                        ApiKeyLocation.HEADER -> headers += Field(name, value)
                        ApiKeyLocation.QUERY -> query += PercentEncoding.encode(name) + "=" + PercentEncoding.encode(value)
                        ApiKeyLocation.COOKIE -> cookies += Field(name, value)
                    }
                }
            }
        }
    }

    private companion object {
        const val ACCEPT = "Accept"
        const val USER_AGENT = "User-Agent"
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_TYPE = "Content-Type"
        const val JSON = "application/json"
    }
}

/** 替换变量后的 baseUrl 是否是 http / https 的绝对地址（PRD 5"baseUrl 不完整"）。 */
public fun isAbsoluteHttpUrl(url: String): Boolean = ABSOLUTE_HTTP_URL.containsMatchIn(url)

/** 该接口所有响应声明的 media type，去重后以逗号连接；没有声明时接受任意类型。 */
public fun Operation.acceptHeader(): String =
    responses.values
        .flatMap {
            (it as? Resolvable.Resolved)
                ?.value
                ?.content
                ?.keys
                .orEmpty()
        }.distinct()
        .ifEmpty { listOf(MediaType.ANY) }
        .joinToString(", ")

/** 由 OkHttp 等传输层生成或管理的 Header；实际请求中不保存它们，重放时也由传输层重新生成。 */
public fun isTransportHeader(name: String): Boolean = name.lowercase() in TRANSPORT_HEADERS

private fun mergeHeaders(
    automatic: List<Field>,
    auth: List<Field>,
    manual: List<Field>,
): List<Field> {
    val manualNames = manual.mapTo(HashSet()) { it.name.lowercase() }
    val authKept = auth.filterNot { it.name.lowercase() in manualNames }
    val overridden = manualNames + authKept.map { it.name.lowercase() }
    return automatic.filterNot { it.name.lowercase() in overridden } + authKept + manual
}

private fun ParameterValue.map(transform: (String) -> String): ParameterValue =
    when (this) {
        is ParameterValue.Text -> ParameterValue.Text(transform(text))
        is ParameterValue.Items -> ParameterValue.Items(items.map(transform))
        is ParameterValue.Fields -> ParameterValue.Fields(fields.map { Field(transform(it.name), transform(it.value)) })
    }

private val ABSOLUTE_HTTP_URL = Regex("^https?://[^/?#\\s]+", RegexOption.IGNORE_CASE)

private val TRANSPORT_HEADERS =
    setOf("host", "connection", "content-length", "transfer-encoding", "accept-encoding", "content-type", "keep-alive", "te", "upgrade")
