package work.foofish.apinspector.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import work.foofish.apinspector.spec.ImportOutcome
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.SpecSummary
import javax.inject.Inject

data class HomeUiState(
    /** 按搜索词过滤后的 API 文档；`null` 表示仍在加载。 */
    val specs: ImmutableList<SpecSummary>?,
    /** 是否有任何 API 文档（不受搜索影响），用于区分"空"与"无匹配"。 */
    val hasSpecs: Boolean,
    val query: String,
    val importStatus: ImportStatus,
) {
    companion object {
        val Initial = HomeUiState(specs = null, hasSpecs = false, query = "", importStatus = ImportStatus.Idle)
    }
}

sealed interface ImportStatus {
    data object Idle : ImportStatus

    data object Importing : ImportStatus

    /** 导入结束，等待界面展示结果后调用 [HomeViewModel.onImportOutcomeShown]。 */
    data class Finished(
        val outcome: ImportOutcome,
    ) : ImportStatus
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val repository: SpecRepository,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)

        val uiState: StateFlow<HomeUiState> =
            combine(repository.specs, query, importStatus) { specs, query, importStatus ->
                HomeUiState(
                    specs = specs.matching(query),
                    hasSpecs = specs.isNotEmpty(),
                    query = query,
                    importStatus = importStatus,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Initial)

        fun onQueryChange(query: String) {
            this.query.value = query
        }

        /** 同一时间只进行一次导入。 */
        fun import(source: ImportSource) {
            if (importStatus.value == ImportStatus.Importing) return
            importStatus.value = ImportStatus.Importing
            viewModelScope.launch { importStatus.value = ImportStatus.Finished(repository.import(source)) }
        }

        fun onImportOutcomeShown() {
            importStatus.update { if (it is ImportStatus.Finished) ImportStatus.Idle else it }
        }

        fun setPinned(
            id: SpecId,
            pinned: Boolean,
        ) {
            viewModelScope.launch { repository.setPinned(id, pinned) }
        }

        fun delete(id: SpecId) {
            viewModelScope.launch { repository.delete(id) }
        }

        private companion object {
            /** 配置变更（如旋转）期间保持上游订阅，避免重新查询。 */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** 按标题过滤，忽略大小写与首尾空白。 */
internal fun List<SpecSummary>.matching(query: String): ImmutableList<SpecSummary> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return toImmutableList()
    return filter { it.title.contains(keyword, ignoreCase = true) }.toImmutableList()
}
