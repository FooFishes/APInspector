package work.foofish.apinspector.operationdoc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import work.foofish.apinspector.navigation.OperationPageKey
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.observeDocument

sealed interface OperationDocUiState {
    data object Loading : OperationDocUiState

    /** API 文档已被删除，或刷新后该接口已不存在。 */
    data object Missing : OperationDocUiState

    data class Failed(
        val failure: ImportFailure,
    ) : OperationDocUiState

    data class Loaded(
        val overview: OperationOverview,
        val items: ImmutableList<DocItem>,
    ) : OperationDocUiState
}

@HiltViewModel(assistedFactory = OperationDocViewModel.Factory::class)
class OperationDocViewModel
    @AssistedInject
    constructor(
        @Assisted key: OperationPageKey,
        specs: SpecRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: OperationPageKey): OperationDocViewModel
        }

        private val operationKey = key.operation
        private val view = MutableStateFlow(DocViewState())

        val uiState: StateFlow<OperationDocUiState> =
            combine(specs.observeDocument(key.specId), view) { load, view ->
                when (load) {
                    DocumentLoad.Missing -> {
                        OperationDocUiState.Missing
                    }

                    is DocumentLoad.Failed -> {
                        OperationDocUiState.Failed(load.failure)
                    }

                    is DocumentLoad.Loaded -> {
                        val document = load.content.document
                        document.operations.firstOrNull { it.key == operationKey }?.let { operation ->
                            OperationDocUiState.Loaded(operation.overview(), buildOperationDoc(operation, document, view))
                        } ?: OperationDocUiState.Missing
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), OperationDocUiState.Loading)

        /** 展开或收起 Schema 节点、参数或响应。 */
        fun toggle(id: String) {
            view.update { it.copy(toggled = if (id in it.toggled) it.toggled - id else it.toggled + id) }
        }

        fun selectContentType(
            ownerId: String,
            contentType: String,
        ) {
            view.update { it.copy(selectedContentTypes = it.selectedContentTypes + (ownerId to contentType)) }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
