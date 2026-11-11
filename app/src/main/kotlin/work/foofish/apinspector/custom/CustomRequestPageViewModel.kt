package work.foofish.apinspector.custom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import work.foofish.apinspector.navigation.CustomRequestKey
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.spec.SpecId

/** 自定义请求页面的标题与归属。 */
sealed interface CustomRequestPageState {
    data object Loading : CustomRequestPageState

    /** 已被删除。 */
    data object Missing : CustomRequestPageState

    /** @property specId 归属的 API 文档；临时请求为 `null`。 */
    data class Loaded(
        val name: String,
        val method: HttpMethod,
        val specId: SpecId?,
    ) : CustomRequestPageState
}

/** 一个自定义请求的页面（PRD 4.2）：请求与响应两个 Tab 之上的标题。 */
@HiltViewModel(assistedFactory = CustomRequestPageViewModel.Factory::class)
class CustomRequestPageViewModel
    @AssistedInject
    constructor(
        @Assisted key: CustomRequestKey,
        customs: CustomRequestRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: CustomRequestKey): CustomRequestPageViewModel
        }

        val uiState: StateFlow<CustomRequestPageState> =
            customs
                .request(key.id)
                .map { request ->
                    if (request == null) {
                        CustomRequestPageState.Missing
                    } else {
                        CustomRequestPageState.Loaded(request.name, request.method, request.specId)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CustomRequestPageState.Loading)

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
