package work.foofish.apinspector.environment.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import work.foofish.apinspector.core.coroutines.ApplicationScope
import work.foofish.apinspector.environment.CookieStore
import work.foofish.apinspector.environment.Environment
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.environment.EnvironmentRepository
import work.foofish.apinspector.navigation.EnvironmentKey
import work.foofish.apinspector.navigation.EnvironmentsKey
import work.foofish.apinspector.openapi.Resolvable
import work.foofish.apinspector.openapi.SecurityScheme
import work.foofish.apinspector.request.EnvironmentOption
import work.foofish.apinspector.request.toOption
import work.foofish.apinspector.spec.DocumentLoad
import work.foofish.apinspector.spec.SpecRepository
import work.foofish.apinspector.spec.observeDocument

/**
 * @property environments 加载中为 `null`。
 */
data class EnvironmentsUiState(
    val environments: ImmutableList<EnvironmentOption>?,
    val current: EnvironmentId?,
)

/** 环境列表（PRD 5"管理环境"）：新建、复制、重命名、删除，至少保留一个。 */
@HiltViewModel(assistedFactory = EnvironmentsViewModel.Factory::class)
class EnvironmentsViewModel
    @AssistedInject
    constructor(
        @Assisted private val key: EnvironmentsKey,
        private val environments: EnvironmentRepository,
    ) : ViewModel(),
        EnvironmentListActions {
        @AssistedFactory
        interface Factory {
            fun create(key: EnvironmentsKey): EnvironmentsViewModel
        }

        val uiState: StateFlow<EnvironmentsUiState> =
            combine(environments.environments(key.specId), environments.current(key.specId)) { all, current ->
                EnvironmentsUiState(all.map(Environment::toOption).toImmutableList(), current?.id)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EnvironmentsUiState(null, null))

        override fun create(
            name: String,
            onCreated: (EnvironmentId) -> Unit,
        ) {
            viewModelScope.launch { onCreated(environments.create(key.specId, name)) }
        }

        override fun duplicate(
            id: EnvironmentId,
            name: String,
        ) {
            viewModelScope.launch { environments.duplicate(id, name) }
        }

        override fun rename(
            id: EnvironmentId,
            name: String,
        ) {
            viewModelScope.launch { environments.find(id)?.let { environments.update(it.copy(name = name)) } }
        }

        fun select(id: EnvironmentId) {
            viewModelScope.launch { environments.select(key.specId, id) }
        }

        override fun delete(id: EnvironmentId) {
            if ((uiState.value.environments?.size ?: 0) <= 1) return
            viewModelScope.launch { environments.delete(id) }
        }
    }

/** 环境列表上的新建、复制、重命名与删除。 */
interface EnvironmentListActions {
    /** 新建后回调其标识，以便打开编辑。 */
    fun create(
        name: String,
        onCreated: (EnvironmentId) -> Unit,
    )

    fun duplicate(
        id: EnvironmentId,
        name: String,
    )

    fun rename(
        id: EnvironmentId,
        name: String,
    )

    /** 至少保留一个环境：只剩一个时不删除。 */
    fun delete(id: EnvironmentId)
}

/** 文档中的一个鉴权方案。 */
data class SchemeOption(
    val name: String,
    val scheme: SecurityScheme,
)

/**
 * @property environment 加载中为 `null`。
 * @property schemes 文档 `securitySchemes` 中可以解析的方案，按文档顺序。
 */
data class EnvironmentUiState(
    val environment: Environment?,
    val missing: Boolean,
    val schemes: ImmutableList<SchemeOption>,
    val cookies: ImmutableList<Cookie>,
)

/** 编辑一个环境（PRD 5、6）：即时保存，不设保存按钮。 */
@HiltViewModel(assistedFactory = EnvironmentViewModel.Factory::class)
class EnvironmentViewModel
    @AssistedInject
    constructor(
        @Assisted key: EnvironmentKey,
        specs: SpecRepository,
        private val environments: EnvironmentRepository,
        private val cookieJar: CookieStore,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(key: EnvironmentKey): EnvironmentViewModel
        }

        private val id = key.environmentId

        /** 本地的环境内容：编辑立即生效，随后在应用作用域中保存；仓库只在首次加载与被删除时采用。 */
        private val local = MutableStateFlow<Local>(Local.Loading)
        private val saving = Mutex()
        private var pending: Environment? = null

        init {
            viewModelScope.launch {
                environments.environment(id).collect { stored ->
                    val current = local.value
                    when {
                        stored == null -> local.value = Local.Missing
                        current !is Local.Loaded -> local.value = Local.Loaded(stored)
                    }
                }
            }
        }

        private val schemes =
            specs.observeDocument(key.specId).map { load ->
                (load as? DocumentLoad.Loaded)
                    ?.content
                    ?.document
                    ?.securitySchemes
                    ?.mapNotNull { (name, scheme) -> (scheme as? Resolvable.Resolved)?.value?.let { SchemeOption(name, it) } }
                    .orEmpty()
                    .toImmutableList()
            }

        val uiState: StateFlow<EnvironmentUiState> =
            combine(local, schemes, cookieJar.cookies(id)) { local, schemes, cookies ->
                EnvironmentUiState(
                    environment = (local as? Local.Loaded)?.environment,
                    missing = local is Local.Missing,
                    schemes = schemes,
                    cookies = cookies.toImmutableList(),
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                EnvironmentUiState(null, missing = false, schemes = persistentListOf(), cookies = persistentListOf()),
            )

        fun update(transform: (Environment) -> Environment) {
            val current = (local.value as? Local.Loaded)?.environment ?: return
            val next = transform(current)
            if (next == current) return
            local.value = Local.Loaded(next)
            pending = next
            applicationScope.launch {
                saving.withLock {
                    val latest = pending ?: return@withLock
                    pending = null
                    environments.update(latest)
                }
            }
        }

        fun deleteCookie(cookie: Cookie) {
            viewModelScope.launch { cookieJar.deleteCookie(id, cookie) }
        }

        fun clearCookies() {
            viewModelScope.launch { cookieJar.clearCookies(id) }
        }

        private sealed interface Local {
            data object Loading : Local

            data object Missing : Local

            data class Loaded(
                val environment: Environment,
            ) : Local
        }
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L
