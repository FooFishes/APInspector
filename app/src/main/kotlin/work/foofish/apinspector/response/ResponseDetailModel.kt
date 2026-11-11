package work.foofish.apinspector.response

import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import work.foofish.apinspector.custom.CustomRequestRepository
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.extraction.ExtractionRuleRepository
import work.foofish.apinspector.extraction.VariableWriter
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.RequestSource
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.network.SendJob
import work.foofish.apinspector.openapi.request.Curl
import work.foofish.apinspector.request.RequestInputRepository
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.request.WorkingParams
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** 响应详情中 Body 的显示方式（PRD 7.2"JSON 折叠树 ⇄ 格式化原文"）。 */
enum class BodyDisplay {
    TREE,
    RAW,
}

/**
 * 响应详情（PRD 7.2）的显示内容。
 *
 * @property rows JSON 折叠树；Body 不是可解析的 JSON、或以原文显示时为 `null`。
 * @property formatted 以原文显示时的内容（JSON 已格式化）。
 */
data class ResponseDetail(
    val entry: HistoryEntry,
    val body: BodyView,
    val display: BodyDisplay,
    val query: String,
    val rows: ImmutableList<JsonViewRow>?,
    val formatted: String?,
    val curl: String,
)

/** 请求记录详情与"响应" Tab 共用的显示状态：加载 Body、折叠与搜索。 */
class ResponseDetailModel(
    private val scope: CoroutineScope,
    entries: Flow<HistoryEntry?>,
    loader: ResponseBodyLoader,
    private val files: FileAccess,
    computation: CoroutineDispatcher,
) {
    private val view = MutableStateFlow(View())
    private val failedSave = MutableStateFlow<String?>(null)

    /** 另存 Body 失败的原因；显示后调用 [dismissSaveFailure]。 */
    val saveFailure: StateFlow<String?> = failedSave

    @OptIn(ExperimentalCoroutinesApi::class)
    private val bodies: Flow<Pair<HistoryEntry, BodyView>?> =
        entries.distinctUntilChanged().mapLatest { entry ->
            entry?.let {
                it to
                    loader.load((it.outcome as? Outcome.Received)?.response?.body, (it.outcome as? Outcome.Received)?.response?.contentType)
            }
        }

    val detail: StateFlow<ResponseDetail?> =
        combine(bodies, view) { loaded, view ->
            loaded?.let { (entry, body) -> present(entry, body, view) }
        }.flowOn(computation).stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun toggle(path: String) {
        view.update { it.copy(collapsed = if (path in it.collapsed) it.collapsed - path else it.collapsed + path) }
    }

    fun setQuery(query: String) {
        view.update { it.copy(query = query) }
    }

    fun setDisplay(display: BodyDisplay) {
        view.update { it.copy(display = display) }
    }

    /** 把 Body 文件另存到用户选择的位置（PRD 7.2）。 */
    fun saveBody(
        file: File,
        to: Uri,
    ) {
        scope.launch {
            try {
                files.save(file, to)
            } catch (exception: IOException) {
                failedSave.value = exception.message ?: exception.javaClass.simpleName
            }
        }
    }

    fun dismissSaveFailure() {
        failedSave.value = null
    }

    private fun present(
        entry: HistoryEntry,
        body: BodyView,
        view: View,
    ): ResponseDetail {
        val json = body as? BodyView.Json
        val element = json?.element
        val display = if (element == null) BodyDisplay.RAW else view.display
        return ResponseDetail(
            entry = entry,
            body = body,
            display = display,
            query = view.query,
            rows = element?.takeIf { display == BodyDisplay.TREE }?.let { jsonViewRows(it, view.collapsed, view.query) },
            formatted =
                when {
                    display != BodyDisplay.RAW -> null
                    element != null -> PrettyJson.encodeToString(JsonElement.serializer(), element)
                    json != null -> json.text
                    body is BodyView.Text -> body.text
                    else -> null
                },
            curl = Curl.export(entry.request),
        )
    }

    private data class View(
        val display: BodyDisplay = BodyDisplay.TREE,
        val collapsed: Set<String> = emptySet(),
        val query: String = "",
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        // prettyPrintIndent 仍是实验性 API：用两个空格缩进，与请求 Body 的格式化一致。
        @OptIn(ExperimentalSerializationApi::class)
        val PrettyJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
    }
}

/** 把响应中 [jsonPath] 处的 [value] 写入变量 [name]；[addRule] 时同时建提取规则。 */
data class SetVariable(
    val name: String,
    val value: String,
    val jsonPath: String,
    val addRule: Boolean,
)

/** 重放、"恢复到当前参数"（PRD 7.1）与"设为变量…"（PRD 5）。 */
class HistoryActions
    @Inject
    constructor(
        private val environments: EnvironmentRepository,
        private val inputs: RequestInputRepository,
        private val customs: CustomRequestRepository,
        private val executor: RequestSender,
        private val writer: VariableWriter,
        private val rules: ExtractionRuleRepository,
    ) {
        /**
         * "设为变量…"（PRD 5）：写入记录所属环境（临时请求则是它自己）的变量，并按需建一条提取规则。
         *
         * @return 是否已写入；环境或请求已被删除时为 `false`，此时也不建规则。
         */
        suspend fun setVariable(
            entry: HistoryEntry,
            request: SetVariable,
        ): Boolean {
            if (!writer.write(entry.ref, entry.environmentId, mapOf(request.name to request.value))) return false
            if (request.addRule) rules.add(entry.ref, request.jsonPath, request.name)
            return true
        }

        /** 原样重发实际请求：不重新替换变量、不注入鉴权、不追加 Cookie；使用所属环境当前的网络设置。 */
        suspend fun replay(entry: HistoryEntry): Boolean {
            val environment = entry.environmentId?.let { environments.find(it) }
            return executor.send(
                SendJob(
                    target = entry.ref,
                    environment = environment,
                    request = entry.request,
                    cookies = emptyList(),
                    loadCookieJar = false,
                    input = entry.input,
                    source = RequestSource.REPLAY,
                    presetName = null,
                ),
            )
        }

        /** 把发送时的当前参数写回该接口（不再关联任何预设）或自定义请求。 */
        suspend fun restore(entry: HistoryEntry) {
            val input = entry.input ?: return
            when (val ref = entry.ref) {
                is RequestRef.Operation -> inputs.saveWorking(ref.specId, ref.key, WorkingParams(input, presetId = null))
                is RequestRef.Custom -> customs.find(ref.id)?.let { customs.update(it.copy(input = input)) }
            }
        }
    }
