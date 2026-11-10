package work.foofish.apinspector.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.ResponseCache
import work.foofish.apinspector.settings.AppSettings
import work.foofish.apinspector.settings.BodyLimit
import work.foofish.apinspector.settings.ProxyConfig
import work.foofish.apinspector.settings.ProxyType
import work.foofish.apinspector.settings.SettingsRepository
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.text.formatFileSize
import javax.inject.Inject

/**
 * @property settings 加载中为 `null`。
 * @property cacheBytes 响应缓存的已用空间；计算中为 `null`。
 */
data class SettingsUiState(
    val settings: AppSettings?,
    val cacheBytes: Long?,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val cache: ResponseCache,
    ) : ViewModel() {
        private val cacheBytes = MutableStateFlow<Long?>(null)

        val uiState: StateFlow<SettingsUiState> =
            combine(repository.settings, cacheBytes, ::SettingsUiState)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState(null, null))

        init {
            viewModelScope.launch { cacheBytes.value = cache.size() }
        }

        fun update(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch { repository.update(transform) }
        }

        /** 清理响应缓存（PRD 6）：只删除 Body 文件，请求记录保留。 */
        fun clearCache() {
            viewModelScope.launch {
                cache.clear()
                cacheBytes.value = cache.size()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** 设置页（PRD 6）：全屏打开。 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        val settings = state.settings ?: return@Scaffold
        Column(
            Modifier
                .padding(
                    innerPadding,
                ).verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = FieldSpacing),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            NetworkSettings(settings, viewModel::update)
            ProxySettings(settings.proxy) { proxy -> viewModel.update { it.copy(proxy = proxy) } }
            StorageSettings(settings.bodyLimit, state.cacheBytes, onLimitChange = { limit ->
                viewModel.update { it.copy(bodyLimit = limit) }
            }, onClear = {
                confirmClear =
                    true
            })
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.settings_clear_cache_title),
            body = stringResource(R.string.settings_clear_cache_body),
            confirmText = stringResource(R.string.settings_clear_cache),
            onConfirm = {
                viewModel.clearCache()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun NetworkSettings(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        SectionTitle(stringResource(R.string.settings_network))
        NumberField(
            value = settings.timeoutSeconds,
            onValueChange = { seconds -> seconds?.let { value -> onUpdate { it.copy(timeoutSeconds = value) } } },
            label = stringResource(R.string.settings_timeout_seconds),
            range = AppSettings.TIMEOUT_RANGE_SECONDS,
            modifier = Modifier.fillMaxWidth(),
            supporting = stringResource(R.string.settings_timeout_hint),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_follow_redirects), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_follow_redirects_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.followRedirects, onCheckedChange = { follow -> onUpdate { it.copy(followRedirects = follow) } })
        }
    }
}

/** 全局代理：直连、HTTP 或 SOCKS5（PRD 6）。 */
@Composable
private fun ProxySettings(
    proxy: ProxyConfig?,
    onChange: (ProxyConfig?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        SectionTitle(stringResource(R.string.settings_proxy))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = proxy == null,
                onClick = { onChange(null) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
                label = { Text(stringResource(R.string.proxy_direct)) },
            )
            SegmentedButton(
                selected = proxy != null,
                onClick = { if (proxy == null) onChange(ProxyConfig(ProxyType.HTTP, host = "", port = 0)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
                label = { Text(stringResource(R.string.proxy_custom)) },
            )
        }
        proxy?.let { ProxyConfigEditor(it, onChange) }
    }
}

/** 响应 Body 保存上限与响应缓存（PRD 7.1）。 */
@Composable
private fun StorageSettings(
    limit: BodyLimit,
    cacheBytes: Long?,
    onLimitChange: (BodyLimit) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        SectionTitle(stringResource(R.string.settings_storage))
        Text(stringResource(R.string.settings_body_limit), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            BodyLimit.entries.forEach { entry ->
                FilterChip(selected = limit == entry, onClick = {
                    onLimitChange(entry)
                }, label = { Text(stringResource(R.string.settings_megabytes, entry.megabytes)) })
            }
        }
        Text(
            stringResource(R.string.settings_body_limit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_cache), style = MaterialTheme.typography.bodyLarge)
                Text(
                    cacheBytes?.let { stringResource(R.string.settings_cache_used, formatFileSize(context, it)) }
                        ?: stringResource(R.string.settings_cache_measuring),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onClear, enabled = (cacheBytes ?: 0) > 0) { Text(stringResource(R.string.settings_clear_cache)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

private val ScreenPadding = 16.dp
private val SectionSpacing = 24.dp
