package work.foofish.apinspector.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.SpecSummary
import javax.inject.Inject

/** 侧边导航中的 API 文档列表。 */
@HiltViewModel
class SpecDrawerViewModel
    @Inject
    constructor(
        repository: SpecRepository,
    ) : ViewModel() {
        val specs: StateFlow<ImmutableList<SpecSummary>> =
            repository.specs
                .map { it.toImmutableList() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), persistentListOf())

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
