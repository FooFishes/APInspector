package work.foofish.apinspector.custom

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.request.EnvironmentVariable
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.spec.SpecId

/** 自定义请求的标识。 */
@JvmInline
@Serializable
value class CustomRequestId(
    val value: String,
)

/**
 * 自定义请求（PRD 4.2）：手动填写方法、URL、Header 与 Body。
 *
 * @property specId 所属的 API 文档；`null` 即临时请求。
 * @property url 临时请求写完整地址；归属 API 文档时可以写相对当前环境 baseUrl 的路径。可以引用变量。
 * @property input Header 与 Query 以额外参数保存，[RequestInput.parameters] 恒为空。
 * @property variables 临时请求自己的变量（ADR-0002）；归属 API 文档时恒为空，变量来自环境。
 */
data class CustomRequest(
    val id: CustomRequestId,
    val specId: SpecId?,
    val name: String,
    val method: HttpMethod,
    val url: String,
    val input: RequestInput,
    val variables: List<EnvironmentVariable>,
) {
    val ref: RequestRef.Custom get() = RequestRef.Custom(id, specId)

    /** 名称为空的变量不参与替换；同名变量以后出现的为准。 */
    val variableValues: Map<String, String> get() = variables.filter { it.name.isNotEmpty() }.associate { it.name to it.value }
}

/** 新建自定义请求时的内容。 */
data class CustomRequestDraft(
    val name: String,
    val method: HttpMethod,
    val url: String,
    val input: RequestInput,
)

/** 自定义请求的存取（PRD 4.2）。 */
interface CustomRequestRepository {
    /** 某份 API 文档的自定义请求；[specId] 为 `null` 时是临时请求。按创建顺序。 */
    fun requests(specId: SpecId?): Flow<List<CustomRequest>>

    fun request(id: CustomRequestId): Flow<CustomRequest?>

    suspend fun find(id: CustomRequestId): CustomRequest?

    suspend fun create(
        specId: SpecId?,
        draft: CustomRequestDraft,
    ): CustomRequestId

    /** 即时保存；不改变归属。 */
    suspend fun update(request: CustomRequest)

    /**
     * 把临时请求移动到 API 文档（单向，PRD 4.2）：地址改为 [url]，它自己的变量随之丢弃，请求记录一并归入该文档。
     */
    suspend fun move(
        id: CustomRequestId,
        specId: SpecId,
        url: String,
    )

    suspend fun delete(id: CustomRequestId)
}
