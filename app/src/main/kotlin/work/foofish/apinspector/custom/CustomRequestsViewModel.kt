package work.foofish.apinspector.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.openapi.request.Curl
import work.foofish.apinspector.openapi.request.CurlError
import work.foofish.apinspector.openapi.request.CurlImport
import work.foofish.apinspector.openapi.request.CurlParse
import work.foofish.apinspector.openapi.request.ExtraLocation
import work.foofish.apinspector.openapi.request.ExtraParameter
import work.foofish.apinspector.openapi.request.RequestInput
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.openapi.request.isAbsoluteHttpUrl
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecRepository

/** 列表中的一个自定义请求。 */
data class CustomRequestItem(
    val id: CustomRequestId,
    val name: String,
    val method: HttpMethod,
    val url: String,
)

/** 可以把临时请求移入的 API 文档。 */
data class MoveTarget(
    val id: SpecId,
    val title: String,
)

/**
 * @property requests 加载中为 `null`。
 * @property moveTargets 临时请求可以移入的 API 文档；列表属于某份 API 文档时为空（移动是单向的，PRD 4.2）。
 */
data class CustomRequestsUiState(
    val requests: ImmutableList<CustomRequestItem>?,
    val moveTargets: ImmutableList<MoveTarget>,
)

/** cURL 导入的结果（PRD 4.2）。 */
sealed interface CurlImportResult {
    /** [ignored]：未识别而被忽略的参数，需要提示。 */
    data class Imported(
        val id: CustomRequestId,
        val ignored: ImmutableList<String>,
    ) : CurlImportResult

    data class Failed(
        val error: CurlError,
    ) : CurlImportResult
}

/** 自定义请求列表上的操作，界面通过它调用而不直接持有 ViewModel。 */
interface CustomRequestListActions {
    /** 新建一个空白的自定义请求。 */
    suspend fun create(name: String): CustomRequestId

    /** 由 cURL 命令生成自定义请求；归属 API 文档时，与当前环境 baseUrl 相同的 URL 前缀改为相对路径。 */
    suspend fun importCurl(command: String): CurlImportResult

    fun rename(
        id: CustomRequestId,
        name: String,
    )

    fun delete(id: CustomRequestId)

    /** 把临时请求移动到 [target]（单向）；与其当前环境 baseUrl 相同的 URL 前缀改为相对路径。 */
    fun move(
        id: CustomRequestId,
        target: SpecId,
    )
}

/** 列表的归属：一份 API 文档，或临时请求（[specId] 为 `null`）。 */
data class CustomRequestsOwner(
    val specId: SpecId?,
)

/**
 * 一份 API 文档的自定义请求，或临时请求的列表（PRD 4.2）：新建、cURL 导入、重命名、移动与删除。
 */
@HiltViewModel(assistedFactory = CustomRequestsViewModel.Factory::class)
class CustomRequestsViewModel
    @AssistedInject
    constructor(
        @Assisted owner: CustomRequestsOwner,
        private val customs: CustomRequestRepository,
        private val environments: EnvironmentRepository,
        specs: SpecRepository,
    ) : ViewModel(),
        CustomRequestListActions {
        @AssistedFactory
        interface Factory {
            fun create(owner: CustomRequestsOwner): CustomRequestsViewModel
        }

        private val specId = owner.specId

        val uiState: StateFlow<CustomRequestsUiState> =
            combine(customs.requests(specId), specs.specs) { requests, specs ->
                CustomRequestsUiState(
                    requests = requests.map { CustomRequestItem(it.id, it.name, it.method, it.url) }.toImmutableList(),
                    moveTargets = if (specId == null) specs.map { MoveTarget(it.id, it.title) }.toImmutableList() else persistentListOf(),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                CustomRequestsUiState(requests = null, moveTargets = persistentListOf()),
            )

        override suspend fun create(name: String): CustomRequestId =
            customs.create(specId, CustomRequestDraft(name, HttpMethod.GET, url = "", input = EmptyInput))

        override suspend fun importCurl(command: String): CurlImportResult =
            when (val parse = Curl.parse(command)) {
                is CurlParse.Failure -> {
                    CurlImportResult.Failed(parse.error)
                }

                is CurlParse.Success -> {
                    val draft = parse.import.toDraft(specId?.let { environments.current(it).first() })
                    CurlImportResult.Imported(customs.create(specId, draft), parse.import.ignored.toImmutableList())
                }
            }

        override fun rename(
            id: CustomRequestId,
            name: String,
        ) {
            viewModelScope.launch { customs.find(id)?.let { customs.update(it.copy(name = name)) } }
        }

        override fun delete(id: CustomRequestId) {
            viewModelScope.launch { customs.delete(id) }
        }

        override fun move(
            id: CustomRequestId,
            target: SpecId,
        ) {
            viewModelScope.launch {
                val request = customs.find(id) ?: return@launch
                customs.move(id, target, relativeUrl(request.url, environments.current(target).first()))
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            val EmptyInput = RequestInput(parameters = emptyList(), extras = emptyList(), body = null)
        }
    }

/** Header 成为额外参数；查询串留在地址里。名称取地址中的路径（方法已由徽章表示）。 */
internal fun CurlImport.toDraft(environment: Environment?): CustomRequestDraft =
    CustomRequestDraft(
        name =
            url
                .substringBefore('?')
                .substringAfter(SCHEME_SEPARATOR)
                .substringAfter('/', "")
                .let { "/$it" },
        method = method,
        url = relativeUrl(url, environment),
        input =
            RequestInput(
                parameters = emptyList(),
                extras = headers.map { ExtraParameter(ExtraLocation.HEADER, it.name, it.value, enabled = true) },
                body = body,
            ),
    )

/**
 * [url] 以 [environment] 的 baseUrl（替换变量后）开头时，改为相对它的路径（PRD 4.2）；否则原样返回。
 */
internal fun relativeUrl(
    url: String,
    environment: Environment?,
): String {
    val base = environment?.let { Variables.resolve(it.baseUrl, it.variableValues).trimEnd('/') }
    if (base == null || !isAbsoluteHttpUrl(base) || !url.startsWith(base)) return url
    val rest = url.removePrefix(base)
    return when {
        rest.isEmpty() -> ROOT_PATH

        rest.startsWith('/') -> rest

        rest.startsWith('?') -> ROOT_PATH + rest

        // 只是字符串前缀相同（例如 `https://api.example.com` 与 `https://api.example.com.cn`），不是同一个 baseUrl。
        else -> url
    }
}

private const val SCHEME_SEPARATOR = "://"
private const val ROOT_PATH = "/"
