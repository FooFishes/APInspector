package work.foofish.apinspector.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import work.foofish.apinspector.navigation.SpecKey
import work.foofish.apinspector.openapi.OperationDiff
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.FavoriteOperationRepository
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.RefreshPreparation
import work.foofish.apinspector.spec.RefreshPreview
import work.foofish.apinspector.spec.SpecOrigin
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.SpecSummary
import work.foofish.apinspector.spec.observeDocument

data class OperationListUiState(
    /** API 文档的元数据；加载中或已删除时为 `null`。 */
    val spec: SpecSummary?,
    val query: String,
    val content: OperationListContent,
    val refresh: RefreshStatus,
) {
    /** 只有 URL 来源可以刷新（PRD 2）。 */
    val canRefresh: Boolean get() = spec?.origin is SpecOrigin.Url

    companion object {
        val Initial = OperationListUiState(spec = null, query = "", content = OperationListContent.Loading, refresh = RefreshStatus.Idle)
    }
}

sealed interface OperationListContent {
    data object Loading : OperationListContent

    data object Missing : OperationListContent

    data class Failed(
        val failure: ImportFailure,
    ) : OperationListContent

    data class Loaded(
        val sections: ImmutableList<OperationSection>,
        /** 文档本身没有任何接口（与"搜索无匹配"区分）。 */
        val hasOperations: Boolean,
        val warnings: ImmutableList<ParseWarning>,
    ) : OperationListContent
}

/** 刷新流程（PRD 2）：拉取 → 有接口消失时确认 → 保存 → 展示变更摘要。 */
sealed interface RefreshStatus {
    data object Idle : RefreshStatus

    data object Fetching : RefreshStatus

    /** 新文档中有接口消失，等待用户确认后才删除它们的数据。 */
    data class ConfirmingRemoval(
        val removed: ImmutableList<OperationKey>,
    ) : RefreshStatus

    data object Applying : RefreshStatus

    data class Done(
        val diff: OperationDiff,
        val warningCount: Int,
    ) : RefreshStatus

    data class Failed(
        val failure: ImportFailure,
    ) : RefreshStatus
}

@HiltViewModel(assistedFactory = OperationListViewModel.Factory::class)
class OperationListViewModel
    @AssistedInject
    constructor(
        @Assisted key: SpecKey,
        private val specs: SpecRepository,
        private val favorites: FavoriteOperationRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: SpecKey): OperationListViewModel
        }

        private val specId = key.specId
        private val query = MutableStateFlow("")
        private val collapsed = MutableStateFlow(emptySet<SectionId>())
        private val refresh = MutableStateFlow<RefreshStatus>(RefreshStatus.Idle)

        /** 等待确认的刷新预览；可能有数 MB，不放进界面状态。 */
        private var pendingRefresh: RefreshPreview? = null

        private val listState = combine(query, collapsed) { query, collapsed -> query to collapsed }

        /** 刷新保存后更新时间变化，这里会自动重新加载。 */
        private val load = specs.observeDocument(specId)

        val uiState: StateFlow<OperationListUiState> =
            combine(
                specs.spec(specId),
                load,
                favorites.favorites(specId),
                listState,
                refresh,
            ) { spec, load, favorites, (query, collapsed), refresh ->
                OperationListUiState(
                    spec = spec,
                    query = query,
                    content = load.toContent(favorites, query, collapsed),
                    refresh = refresh,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OperationListUiState.Initial)

        fun onQueryChange(query: String) {
            this.query.value = query
        }

        fun toggleSection(id: SectionId) {
            collapsed.update { if (id in it) it - id else it + id }
        }

        fun setFavorite(
            operation: OperationKey,
            favorite: Boolean,
        ) {
            viewModelScope.launch { favorites.setFavorite(specId, operation, favorite) }
        }

        fun refresh() {
            if (refresh.value != RefreshStatus.Idle) return
            refresh.value = RefreshStatus.Fetching
            viewModelScope.launch {
                when (val preparation = specs.prepareRefresh(specId)) {
                    is RefreshPreparation.Failed -> {
                        refresh.value = RefreshStatus.Failed(preparation.failure)
                    }

                    is RefreshPreparation.Ready -> {
                        val removed = preparation.preview.diff.removed
                        if (removed.isEmpty()) {
                            apply(preparation.preview)
                        } else {
                            pendingRefresh = preparation.preview
                            refresh.value = RefreshStatus.ConfirmingRemoval(removed.toImmutableList())
                        }
                    }
                }
            }
        }

        fun confirmRefresh() {
            val preview = pendingRefresh ?: return
            pendingRefresh = null
            viewModelScope.launch { apply(preview) }
        }

        fun cancelRefresh() {
            pendingRefresh = null
            refresh.value = RefreshStatus.Idle
        }

        /** 界面展示完刷新结果（摘要或失败）后调用。 */
        fun onRefreshResultShown() {
            refresh.update { if (it is RefreshStatus.Done || it is RefreshStatus.Failed) RefreshStatus.Idle else it }
        }

        private suspend fun apply(preview: RefreshPreview) {
            refresh.value = RefreshStatus.Applying
            specs.applyRefresh(preview)
            refresh.value = RefreshStatus.Done(preview.diff, preview.content.warnings.size)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun DocumentLoad.toContent(
    favorites: Set<OperationKey>,
    query: String,
    collapsed: Set<SectionId>,
): OperationListContent =
    when (this) {
        DocumentLoad.Missing -> {
            OperationListContent.Missing
        }

        is DocumentLoad.Failed -> {
            OperationListContent.Failed(failure)
        }

        is DocumentLoad.Loaded -> {
            OperationListContent.Loaded(
                sections = buildSections(content.document, favorites, query, collapsed),
                hasOperations = content.document.operations.isNotEmpty(),
                warnings = content.warnings.toImmutableList(),
            )
        }
    }
