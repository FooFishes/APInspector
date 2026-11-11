package work.foofish.apinspector.request

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import work.foofish.apinspector.custom.CustomRequest
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.openapi.Info
import work.foofish.apinspector.openapi.MediaTypeContent
import work.foofish.apinspector.openapi.OpenApiDocument
import work.foofish.apinspector.openapi.OpenApiVersion
import work.foofish.apinspector.openapi.Operation
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.RequestBody
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.SecurityRequirement
import work.foofish.apinspector.spec.SpecId

/** "请求" Tab 编辑的对象：接口的当前参数，或一个自定义请求（PRD 4.2）。 */
sealed interface RequestTarget {
    data class Operation(
        val specId: SpecId,
        val key: OperationKey,
    ) : RequestTarget

    data class Custom(
        val id: CustomRequestId,
    ) : RequestTarget
}

/** 临时请求不属于任何 API 文档：以一份空文档充当编辑时的上下文。 */
internal val EmptyDocument: OpenApiDocument =
    OpenApiDocument(
        version = OpenApiVersion.V3_1,
        declaredVersion = "",
        info = Info(title = "", version = null, summary = null, description = null),
        servers = emptyList(),
        tags = emptyList(),
        operations = emptyList(),
        securitySchemes = emptyMap(),
        schemas = emptyMap(),
    )

/**
 * 把自定义请求表示成一个没有声明参数的接口，这样参数与 Body 的编辑可以与接口共用同一套逻辑。
 *
 * Body 可选自定义请求支持的各种类型（PRD 4.3），外加它当前使用的类型（例如由 cURL 导入的 `application/xml`）。
 * 鉴权沿用 [document] 中最常见的要求——接口未单独声明时继承的就是文档顶层的 `security`。
 */
internal fun CustomRequest.asOperation(document: OpenApiDocument): Operation {
    val contentTypes = (CUSTOM_CONTENT_TYPES + listOfNotNull(input.body?.contentType)).distinct()
    val empty = Resolvable.Resolved(MediaTypeContent(schema = null, itemSchema = null, example = null, examples = emptyMap()))
    return Operation(
        key = OperationKey(method, CUSTOM_PATH),
        operationId = null,
        summary = name,
        description = null,
        tags = emptyList(),
        deprecated = false,
        parameters = emptyList(),
        requestBody =
            Resolvable.Resolved(
                RequestBody(description = null, required = false, content = contentTypes.associateWith { empty }),
            ),
        responses = emptyMap(),
        security = document.prevailingSecurity(),
    )
}

private fun OpenApiDocument.prevailingSecurity(): List<SecurityRequirement> =
    operations
        .groupingBy { it.security }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        .orEmpty()

/**
 * 本地优先的编辑：编辑立即生效，随后在应用作用域中保存最新的值——离开页面也会完成。
 *
 * 记住自己写入过的值：仓库把它们回传时不必采用，以免覆盖之后的编辑。
 */
internal class LocalFirstSaver<T : Any>(
    private val scope: CoroutineScope,
    private val save: suspend (T) -> Unit,
) {
    private val saving = Mutex()
    private var pending: T? = null
    private var persisted: T? = null
    private val written = LinkedHashSet<T>()

    fun submit(value: T) {
        pending = value
        remember(value)
        scope.launch {
            saving.withLock {
                val next = pending ?: return@withLock
                if (next != persisted) {
                    save(next)
                    persisted = next
                }
            }
        }
    }

    /** 记住一个由别处写入、但内容出自这里的值。 */
    fun remember(value: T) {
        synchronized(written) {
            written += value
            if (written.size > REMEMBERED_WRITES) written.remove(written.first())
        }
    }

    fun isOurs(value: T): Boolean = synchronized(written) { value in written }

    private companion object {
        const val REMEMBERED_WRITES = 32
    }
}

/** 自定义请求可选的 Body 类型（PRD 4.3）。 */
private val CUSTOM_CONTENT_TYPES =
    listOf(
        "application/json",
        "application/x-www-form-urlencoded",
        "multipart/form-data",
        "application/octet-stream",
        "text/plain",
    )

/** 充当接口路径的占位：自定义请求的地址在 [CustomRequest.url] 中。 */
private const val CUSTOM_PATH = "/"
