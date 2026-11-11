package work.foofish.apinspector.openapi.request

import work.foofish.apinspector.openapi.HttpMethod
import java.util.Base64

/** 由 cURL 命令得到的自定义请求内容（PRD 4.2）。 */
public data class CurlImport(
    val method: HttpMethod,
    val url: String,
    /** 不含 `Content-Type`（由 [body] 的 content type 表达）；没有 Body 时原样保留。 */
    val headers: List<Field>,
    val body: BodyInput?,
    /** 未识别而被忽略的参数（如 `--insecure`），按出现顺序，用于提示。 */
    val ignored: List<String>,
)

public sealed interface CurlParse {
    public data class Success(
        val import: CurlImport,
    ) : CurlParse

    public data class Failure(
        val error: CurlError,
    ) : CurlParse
}

public enum class CurlError {
    /** 不是以 `curl` 开头的命令。 */
    NOT_CURL,
    NO_URL,
    UNTERMINATED_QUOTE,

    /** `-X` 给出的不是合法的 HTTP 方法。 */
    UNKNOWN_METHOD,
}

/** 解析 cURL 命令：先按 shell 规则分词，再逐个处理参数。 */
internal object CurlParser {
    fun parse(command: String): CurlParse {
        val words = ShellWords.split(command) ?: return CurlParse.Failure(CurlError.UNTERMINATED_QUOTE)
        // 从终端复制时常带着提示符。
        val arguments = words.dropWhile { it == PROMPT }
        val program = arguments.firstOrNull()?.substringAfterLast('/')?.substringAfterLast('\\')
        return if (program !in PROGRAMS) {
            CurlParse.Failure(CurlError.NOT_CURL)
        } else {
            CurlArguments().apply { read(arguments.drop(1)) }.build()
        }
    }

    private fun CurlArguments.read(words: List<String>) {
        val iterator = words.iterator()
        while (iterator.hasNext()) {
            val word = iterator.next()
            when {
                word.startsWith(LONG_PREFIX) -> if (word != LONG_PREFIX) option(word, inline = null, iterator)
                word.startsWith(SHORT_PREFIX) && word.length > 1 -> shortOptions(word, iterator)
                else -> url(word)
            }
        }
    }

    /** 短参数可以合并（`-sSL`），带值的参数其值可以紧跟其后（`-XPOST`、`-H'a: b'`）。 */
    private fun CurlArguments.shortOptions(
        word: String,
        rest: Iterator<String>,
    ) {
        var index = 1
        while (index < word.length) {
            val name = SHORT_PREFIX + word[index++]
            if (name in VALUE_OPTIONS || name in SWALLOWED_OPTIONS) {
                option(name, inline = word.substring(index).takeIf { it.isNotEmpty() }, rest)
                index = word.length
            } else {
                option(name, inline = null, rest)
            }
        }
    }

    private fun CurlArguments.option(
        name: String,
        inline: String?,
        rest: Iterator<String>,
    ) {
        val flag = FLAG_OPTIONS[name]
        val valued = VALUE_OPTIONS[name]
        when {
            name in SILENT_OPTIONS -> {}

            flag != null -> {
                flag(this)
            }

            valued != null -> {
                (inline ?: rest.nextOrNull())?.let { valued(this, it) }
            }

            else -> {
                // 带值的参数连同值一起忽略，以免把值当成 URL。
                if (name in SWALLOWED_OPTIONS && inline == null) rest.nextOrNull()
                ignored += name
            }
        }
    }

    private fun Iterator<String>.nextOrNull(): String? = if (hasNext()) next() else null

    private const val PROMPT = "$"
    private const val SHORT_PREFIX = "-"
    private const val LONG_PREFIX = "--"
    private val PROGRAMS = setOf("curl", "curl.exe")

    /** 只影响输出、对请求本身没有意义的参数：接受并且不提示。 */
    private val SILENT_OPTIONS =
        setOf("--compressed", "-s", "--silent", "-S", "--show-error", "-v", "--verbose", "-i", "--include", "--no-progress-meter")

    private val FLAG_OPTIONS: Map<String, (CurlArguments) -> Unit> =
        mapOf<String, (CurlArguments) -> Unit>(
            "-G" to { it.get = true },
            "--get" to { it.get = true },
            "-I" to { it.head = true },
            "--head" to { it.head = true },
        )

    private val VALUE_OPTIONS: Map<String, (CurlArguments, String) -> Unit> =
        mapOf<String, (CurlArguments, String) -> Unit>(
            "-X" to CurlArguments::method,
            "--request" to CurlArguments::method,
            "--url" to CurlArguments::url,
            "-H" to CurlArguments::header,
            "--header" to CurlArguments::header,
            "-d" to CurlArguments::data,
            "--data" to CurlArguments::data,
            "--data-raw" to CurlArguments::data,
            "--data-binary" to CurlArguments::data,
            "--data-ascii" to CurlArguments::data,
            "--data-urlencode" to CurlArguments::dataUrlEncoded,
            "--json" to CurlArguments::json,
            "-F" to CurlArguments::form,
            "--form" to CurlArguments::form,
            "--form-string" to CurlArguments::formString,
            "-u" to CurlArguments::user,
            "--user" to CurlArguments::user,
            "-A" to { arguments, value -> arguments.headers += Field(USER_AGENT, value) },
            "--user-agent" to { arguments, value -> arguments.headers += Field(USER_AGENT, value) },
            "-e" to { arguments, value -> arguments.headers += Field(REFERER, value) },
            "--referer" to { arguments, value -> arguments.headers += Field(REFERER, value) },
            "-b" to CurlArguments::cookie,
            "--cookie" to CurlArguments::cookie,
        )

    /** 不支持、但带一个值的常见参数。 */
    private val SWALLOWED_OPTIONS =
        setOf(
            "-o",
            "--output",
            "--connect-timeout",
            "-m",
            "--max-time",
            "-x",
            "--proxy",
            "-U",
            "--proxy-user",
            "--noproxy",
            "-E",
            "--cert",
            "--cert-type",
            "--key",
            "--key-type",
            "--pass",
            "--cacert",
            "--capath",
            "--ciphers",
            "-w",
            "--write-out",
            "--retry",
            "--retry-delay",
            "--retry-max-time",
            "-c",
            "--cookie-jar",
            "-T",
            "--upload-file",
            "--resolve",
            "--interface",
            "-K",
            "--config",
            "--limit-rate",
            "--max-redirs",
            "-r",
            "--range",
            "-D",
            "--dump-header",
            "--trace",
            "--trace-ascii",
            "--stderr",
            "--unix-socket",
            "-z",
            "--time-cond",
            "-y",
            "--speed-time",
            "-Y",
            "--speed-limit",
            "--max-filesize",
            "--proto",
            "-C",
            "--continue-at",
        )
}

/** 逐个参数累积出来的请求内容。 */
private class CurlArguments {
    var get = false
    var head = false
    val headers = mutableListOf<Field>()
    val ignored = mutableListOf<String>()
    private var method: String? = null
    private var url: String? = null
    private var json = false
    private val data = mutableListOf<String>()
    private val parts = mutableListOf<MultipartPart>()

    fun method(value: String) {
        method = value
    }

    /** 只取第一个 URL；其余的作为被忽略的参数提示。 */
    fun url(value: String) {
        if (url == null) url = value else ignored += value
    }

    /** `Name: value`；`Name;` 是值为空的 Header；`Name:`（cURL 中表示去掉该 Header）与无法识别的写法忽略。 */
    fun header(value: String) {
        val name = value.substringBefore(HEADER_SEPARATOR).trim()
        val content = value.substringAfter(HEADER_SEPARATOR, missingDelimiterValue = "").trim()
        when {
            HEADER_SEPARATOR in value && name.isNotEmpty() && content.isNotEmpty() -> headers += Field(name, content)
            value.endsWith(EMPTY_HEADER_MARK) -> headers += Field(value.dropLast(1).trim(), "")
        }
    }

    fun data(value: String) {
        data += value
    }

    /** `content`、`=content`、`name=content`：只编码内容部分。 */
    fun dataUrlEncoded(value: String) {
        val name = value.substringBefore(PAIR_SEPARATOR, missingDelimiterValue = "")
        val content = value.substringAfter(PAIR_SEPARATOR)
        data += (if (name.isEmpty()) "" else name + PAIR_SEPARATOR) + PercentEncoding.encodeForm(content)
    }

    fun json(value: String) {
        data += value
        json = true
    }

    /** `name=value` 为文本字段；`name=@path` / `name=<path` 引用本机文件，导入后需要重新选择。 */
    fun form(value: String) {
        val content = value.substringAfter(PAIR_SEPARATOR, missingDelimiterValue = "")
        val file = content.startsWith(FILE_MARK) || content.startsWith(FILE_CONTENT_MARK)
        parts += MultipartPart(value.substringBefore(PAIR_SEPARATOR), if (file) PartValue.File(file = null) else PartValue.Text(content))
    }

    fun formString(value: String) {
        parts += MultipartPart(value.substringBefore(PAIR_SEPARATOR), PartValue.Text(value.substringAfter(PAIR_SEPARATOR, "")))
    }

    fun user(value: String) {
        val pair = if (USER_SEPARATOR in value) value else value + USER_SEPARATOR
        headers += Field(AUTHORIZATION, BASIC_PREFIX + Base64.getEncoder().encodeToString(pair.encodeToByteArray()))
    }

    /** `name=value; …` 作为 `Cookie` Header；不含 `=` 时是 Cookie 文件，无法使用。 */
    fun cookie(value: String) {
        if (PAIR_SEPARATOR in value) headers += Field(COOKIE, value) else ignored += COOKIE_OPTION
    }

    fun build(): CurlParse {
        val target = url
        val name = method ?: defaultMethod()
        val parsed = HttpMethod.of(name)
        return when {
            target == null -> CurlParse.Failure(CurlError.NO_URL)
            parsed == null -> CurlParse.Failure(CurlError.UNKNOWN_METHOD)
            else -> CurlParse.Success(import(parsed, target))
        }
    }

    private fun defaultMethod(): String =
        when {
            head -> HttpMethod.HEAD.name
            get -> HttpMethod.GET.name
            data.isNotEmpty() || parts.isNotEmpty() -> HttpMethod.POST.name
            else -> HttpMethod.GET.name
        }

    private fun import(
        method: HttpMethod,
        target: String,
    ): CurlImport {
        val payload = data.joinToString(DATA_SEPARATOR)
        // -G：数据进查询串，不作为 Body。
        val inQuery = get && data.isNotEmpty()
        val url = if (inQuery) target + (if (QUERY_MARK in target) DATA_SEPARATOR else QUERY_MARK) + payload else target
        val explicitType = headers.lastOrNull { it.name.equals(CONTENT_TYPE, ignoreCase = true) }?.value
        val body =
            when {
                parts.isNotEmpty() -> BodyInput.Multipart(MediaType.MULTIPART_FORM_DATA, parts.toList())
                data.isNotEmpty() && !inQuery -> textBody(explicitType ?: if (json) JSON else MediaType.FORM_URLENCODED, payload)
                else -> null
            }
        val kept = if (body == null) headers.toList() else headers.filterNot { it.name.equals(CONTENT_TYPE, ignoreCase = true) }
        val accept = if (json && kept.none { it.name.equals(ACCEPT, ignoreCase = true) }) listOf(Field(ACCEPT, JSON)) else emptyList()
        return CurlImport(method, url, kept + accept, body, ignored.toList())
    }

    private fun textBody(
        contentType: String,
        text: String,
    ): BodyInput {
        val essence = MediaType.essence(contentType)
        return when {
            essence == JSON || essence.endsWith(JSON_SUFFIX) -> BodyInput.Json(contentType, text)
            essence == MediaType.FORM_URLENCODED -> BodyInput.Form(contentType, FormText.parse(text))
            else -> BodyInput.Text(contentType, text)
        }
    }

    private companion object {
        const val HEADER_SEPARATOR = ':'
        const val EMPTY_HEADER_MARK = ";"
        const val PAIR_SEPARATOR = "="
        const val DATA_SEPARATOR = "&"
        const val QUERY_MARK = "?"
        const val FILE_MARK = "@"
        const val FILE_CONTENT_MARK = "<"
        const val USER_SEPARATOR = ":"
        const val BASIC_PREFIX = "Basic "
        const val COOKIE_OPTION = "--cookie"
        const val JSON = "application/json"
        const val JSON_SUFFIX = "+json"
    }
}

private const val CONTENT_TYPE = "Content-Type"
private const val ACCEPT = "Accept"
private const val AUTHORIZATION = "Authorization"
private const val USER_AGENT = "User-Agent"
private const val REFERER = "Referer"
private const val COOKIE = "Cookie"
