package work.foofish.apinspector.response

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.request.RequestRef
import work.foofish.apinspector.request.RequestTarget
import work.foofish.apinspector.request.isSameRequest
import java.io.File
import java.time.Instant

/**
 * @property sendingSince 请求进行中时为开始时间。
 * @property restored 刚把记录恢复到当前参数，界面切到"请求" Tab 后调用 [ResponseViewModel.onRestoredShown]。
 */
data class ResponseUiState(
    val content: ResponseContent,
    val sendingSince: Instant?,
    val restored: Boolean,
)

sealed interface ResponseContent {
    data object Loading : ResponseContent

    /** 该接口还没有请求记录。 */
    data object Empty : ResponseContent

    data class Ready(
        val detail: ResponseDetail,
    ) : ResponseContent
}

/** "响应" Tab（PRD 3）：该接口最近一条请求记录的响应详情。 */
@HiltViewModel(assistedFactory = ResponseViewModel.Factory::class)
class ResponseViewModel
    @AssistedInject
    constructor(
        @Assisted target: RequestTarget,
        history: HistoryRepository,
        loader: ResponseBodyLoader,
        private val executor: RequestSender,
        private val actions: HistoryActions,
        files: FileAccess,
        @DefaultDispatcher computation: CoroutineDispatcher,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(target: RequestTarget): ResponseViewModel
        }

        /** 自定义请求的记录只以它的标识查找，与它当前归属哪份 API 文档无关。 */
        private val ref: RequestRef =
            when (target) {
                is RequestTarget.Operation -> RequestRef.Operation(target.specId, target.key)
                is RequestTarget.Custom -> RequestRef.Custom(target.id, specId = null)
            }
        private val latest: StateFlow<Latest> =
            history
                .latest(ref)
                .map<HistoryEntry?, Latest>(Latest::Known)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Latest.Unknown)
        private val model = ResponseDetailModel(viewModelScope, latest.map { (it as? Latest.Known)?.entry }, loader, files, computation)
        private val restored = MutableStateFlow(false)

        /** "设为变量…"没有可写入的环境。 */
        val variableFailed = MutableStateFlow(false)

        val uiState: StateFlow<ResponseUiState> =
            combine(
                latest,
                model.detail,
                executor.inFlight
                    .map { flights ->
                        flights.entries.firstOrNull { it.key.isSameRequest(ref) }?.value
                    }.distinctUntilChanged(),
                restored,
            ) { latest, detail, since, restored ->
                val content =
                    when {
                        latest !is Latest.Known -> ResponseContent.Loading
                        latest.entry == null -> ResponseContent.Empty
                        detail == null || detail.entry.id != latest.entry.id -> ResponseContent.Loading
                        else -> ResponseContent.Ready(detail)
                    }
                ResponseUiState(content, since, restored)
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                ResponseUiState(ResponseContent.Loading, null, restored = false),
            )

        fun toggle(path: String) = model.toggle(path)

        fun setQuery(query: String) = model.setQuery(query)

        fun setDisplay(display: BodyDisplay) = model.setDisplay(display)

        /** 另存 Body 失败的原因。 */
        val saveFailure: StateFlow<String?> = model.saveFailure

        fun saveBody(
            file: File,
            to: Uri,
        ) = model.saveBody(file, to)

        fun dismissSaveFailure() = model.dismissSaveFailure()

        fun cancel() {
            executor.inFlight.value.keys
                .firstOrNull { it.isSameRequest(ref) }
                ?.let(executor::cancel)
        }

        fun replay() {
            val entry = (latest.value as? Latest.Known)?.entry ?: return
            viewModelScope.launch { actions.replay(entry) }
        }

        fun restore() {
            val entry = (latest.value as? Latest.Known)?.entry ?: return
            viewModelScope.launch {
                actions.restore(entry)
                restored.value = true
            }
        }

        /** 写入失败（环境已被删除）时 [variableFailed] 置位。 */
        fun setVariable(request: SetVariable) {
            val entry = (latest.value as? Latest.Known)?.entry ?: return
            viewModelScope.launch { variableFailed.value = !actions.setVariable(entry, request) }
        }

        fun onVariableFailureShown() {
            variableFailed.value = false
        }

        fun onRestoredShown() {
            restored.value = false
        }

        private sealed interface Latest {
            data object Unknown : Latest

            data class Known(
                val entry: HistoryEntry?,
            ) : Latest
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
