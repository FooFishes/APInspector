package work.foofish.apinspector.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
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
import work.foofish.apinspector.ui.component.ChoiceGroup
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.SectionCard
import work.foofish.apinspector.ui.component.SectionLabel
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.screen,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                colors = screenTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val settings = state.settings
        if (settings == null) {
            LoadingState(Modifier.padding(innerPadding))
            return@Scaffold
        }
        Column(
            Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(bottom = BottomPadding),
        ) {
            NetworkSettings(settings, viewModel::update)
            ProxySettings(settings.proxy) { proxy -> viewModel.update { it.copy(proxy = proxy) } }
            StorageSettings(
                limit = settings.bodyLimit,
                cacheBytes = state.cacheBytes,
                onLimitChange = { limit -> viewModel.update { it.copy(bodyLimit = limit) } },
                onClear = { confirmClear = true },
            )
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
            destructive = true,
        )
    }
}

@Composable
private fun NetworkSettings(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionLabel(stringResource(R.string.settings_network))
    SectionCard {
        NumberField(
            value = settings.timeoutSeconds,
            onValueChange = { seconds -> seconds?.let { value -> onUpdate { it.copy(timeoutSeconds = value) } } },
            label = stringResource(R.string.settings_timeout_seconds),
            range = AppSettings.TIMEOUT_RANGE_SECONDS,
            modifier = Modifier.fillMaxWidth(),
            supporting = stringResource(R.string.settings_timeout_hint),
        )
        SettingRow(
            title = stringResource(R.string.settings_follow_redirects),
            description = stringResource(R.string.settings_follow_redirects_hint),
            modifier = Modifier.padding(top = FieldSpacing),
        ) {
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
    SectionLabel(stringResource(R.string.settings_proxy))
    SectionCard {
        ChoiceGroup(
            options = ProxyModes,
            selected = proxy != null,
            onSelect = { custom -> onChange(if (custom) ProxyConfig(ProxyType.HTTP, host = "", port = 0) else null) },
            label = { custom -> stringResource(if (custom) R.string.proxy_custom else R.string.proxy_direct) },
            modifier = Modifier.fillMaxWidth(),
            fill = true,
        )
        AnimatedVisibility(visible = proxy != null) {
            proxy?.let { ProxyConfigEditor(it, onChange, Modifier.padding(top = SectionSpacing)) }
        }
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
    SectionLabel(stringResource(R.string.settings_storage))
    SectionCard {
        Text(stringResource(R.string.settings_body_limit), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_body_limit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(modifier = Modifier.padding(top = FieldSpacing), horizontalArrangement = Arrangement.spacedBy(FieldSpacing)) {
            BodyLimit.entries.forEach { entry ->
                FilterChip(
                    selected = limit == entry,
                    onClick = { onLimitChange(entry) },
                    label = { Text(stringResource(R.string.settings_megabytes, entry.megabytes)) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = SectionSpacing), color = MaterialTheme.colorScheme.outlineVariant)
        SettingRow(
            title = stringResource(R.string.settings_cache),
            description =
                cacheBytes?.let { stringResource(R.string.settings_cache_used, formatFileSize(context, it)) }
                    ?: stringResource(R.string.settings_cache_measuring),
        ) {
            FilledTonalButton(onClick = onClear, enabled = (cacheBytes ?: 0) > 0) { Text(stringResource(R.string.settings_clear_cache)) }
        }
    }
}

/** 一项设置：左侧标题与说明，右侧是它的控件。 */
@Composable
private fun SettingRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(SectionSpacing), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

/** 全局代理的两种模式：直连（`false`）与自定义（`true`）。 */
private val ProxyModes = persistentListOf(false, true)
private val SectionSpacing = 16.dp
private val BottomPadding = 32.dp
