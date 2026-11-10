package work.foofish.apinspector.history.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.foofish.apinspector.core.coroutines.DefaultDispatcher
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.HistoryRepository
import work.foofish.apinspector.history.HistorySummary
import work.foofish.apinspector.navigation.HistoryEntryKey
import work.foofish.apinspector.navigation.HistoryKey
import work.foofish.apinspector.network.FileAccess
import work.foofish.apinspector.network.RequestSender
import work.foofish.apinspector.network.SendTarget
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.response.BodyDisplay
import work.foofish.apinspector.response.HistoryActions
import work.foofish.apinspector.response.ResponseBodyLoader
import work.foofish.apinspector.response.ResponseDetail
import work.foofish.apinspector.response.ResponseDetailModel
import java.io.File

/**
 * @property entries 从新到旧；加载中为 `null`。
 * @property operation 只列出该接口的记录；为 `null` 时列出全部。
 */
data class HistoryUiState(
    val entries: ImmutableList<HistorySummary>?,
    val operation: OperationKey?,
)

/** 一份 API 文档的请求记录列表（PRD 7.1）。 */
@HiltViewModel(assistedFactory = HistoryViewModel.Factory::class)
class HistoryViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: HistoryKey,
        private val history: HistoryRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: HistoryKey): HistoryViewModel
        }

        val uiState: StateFlow<HistoryUiState> =
            history
                .summaries(key.specId, key.operation)
                .map { HistoryUiState(it.toImmutableList(), key.operation) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HistoryUiState(null, key.operation))

        /** 清空该 API 文档的全部记录（PRD 7.1"手动清空"）。 */
        fun clear() {
            viewModelScope.launch { history.clear(key.specId) }
        }
    }

/**
 * @property detail 加载中为 `null`。
 * @property missing 记录已被清空或淘汰。
 */
data class HistoryEntryUiState(
    val detail: ResponseDetail?,
    val missing: Boolean,
    val sending: Boolean,
)

/** 一条请求记录的详情：与"响应" Tab 相同的响应详情，可重放与恢复到当前参数（PRD 7）。 */
@HiltViewModel(assistedFactory = HistoryEntryViewModel.Factory::class)
class HistoryEntryViewModel
    @AssistedInject
    constructor(
        @Assisted key: HistoryEntryKey,
        history: HistoryRepository,
        loader: ResponseBodyLoader,
        files: FileAccess,
        private val executor: RequestSender,
        private val actions: HistoryActions,
        @DefaultDispatcher computation: CoroutineDispatcher,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: HistoryEntryKey): HistoryEntryViewModel
        }

        private val entry: StateFlow<HistoryEntry?> = history.entry(key.entry).stateIn(viewModelScope, SharingStarted.Eagerly, null)
        private val model = ResponseDetailModel(viewModelScope, entry, loader, files, computation)

        /** 记录已不存在（被清空或淘汰）；与尚未加载区分开。 */
        private val missing = history.entry(key.entry).map { it == null }

        val uiState: StateFlow<HistoryEntryUiState> =
            combine(model.detail, missing, executor.inFlight) { detail, missing, inFlight ->
                HistoryEntryUiState(
                    detail = detail,
                    missing = missing,
                    sending = detail?.entry?.let { SendTarget(it.specId, it.operation) in inFlight } == true,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                HistoryEntryUiState(null, missing = false, sending = false),
            )

        val saveFailure: StateFlow<String?> = model.saveFailure

        /** 取消该记录所属接口的在途请求（例如刚从这里重放的请求）。 */
        fun cancel() {
            entry.value?.let { executor.cancel(SendTarget(it.specId, it.operation)) }
        }

        fun toggle(path: String) = model.toggle(path)

        fun setQuery(query: String) = model.setQuery(query)

        fun setDisplay(display: BodyDisplay) = model.setDisplay(display)

        fun saveBody(
            file: File,
            to: Uri,
        ) = model.saveBody(file, to)

        fun dismissSaveFailure() = model.dismissSaveFailure()

        fun replay() {
            val current = entry.value ?: return
            viewModelScope.launch { actions.replay(current) }
        }

        fun restore() {
            val current = entry.value ?: return
            viewModelScope.launch { actions.restore(current) }
        }
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L
