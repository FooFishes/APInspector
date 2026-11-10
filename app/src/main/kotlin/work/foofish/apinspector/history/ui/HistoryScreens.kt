package work.foofish.apinspector.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.history.HistorySummary
import work.foofish.apinspector.history.OutcomeKind
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.response.ui.ActionChip
import work.foofish.apinspector.response.ui.ResponseDetailActions
import work.foofish.apinspector.response.ui.ResponseDetailContent
import work.foofish.apinspector.response.ui.SaveFailureDialog
import work.foofish.apinspector.response.ui.SendingBanner
import work.foofish.apinspector.response.ui.StatusBadge
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.OperationTitle
import work.foofish.apinspector.ui.text.formatDuration
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.text.relativeTime
import work.foofish.apinspector.ui.theme.codeMedium

/** 一份 API 文档的请求记录列表（PRD 7.1）：从新到旧，可按接口筛选、清空。 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    selected: HistoryEntryId?,
    onOpen: (HistoryEntryId) -> Unit,
    onShowAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { HistoryTitle(state.operation) },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                actions = {
                    if (state.operation != null) {
                        IconButton(onClick = onShowAll) {
                            Icon(painterResource(R.drawable.ic_filter_list), contentDescription = stringResource(R.string.history_show_all))
                        }
                    }
                    HistoryMenu(enabled = state.entries?.isNotEmpty() == true, onClear = { confirmClear = true })
                },
            )
        },
    ) { innerPadding ->
        val entries = state.entries
        when {
            entries == null -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            entries.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(painterResource(R.drawable.ic_history), stringResource(R.string.history_empty), body = null)
                }
            }

            else -> {
                LazyColumn(contentPadding = innerPadding) {
                    items(entries, key = { it.id.value }) { summary ->
                        HistoryRow(summary, selected = summary.id == selected, onClick = { onOpen(summary.id) })
                    }
                }
            }
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_body),
            confirmText = stringResource(R.string.history_clear),
            onConfirm = {
                viewModel.clear()
                confirmClear = false
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun HistoryMenu(
    enabled: Boolean,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_clear)) },
                enabled = enabled,
                onClick = {
                    expanded = false
                    onClear()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            )
        }
    }
}

/** 每行：方法、路径、状态码（着色）、耗时、大小与相对时间；网络错误与取消单独标出。 */
@Composable
private fun HistoryRow(
    summary: HistorySummary,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        onClick = onClick,
        selected = selected,
        leadingContent = { MethodBadge(summary.operation.method) },
        content = {
            Text(
                summary.operation.path,
                style = MaterialTheme.typography.codeMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(RowSpacing), itemVerticalAlignment = Alignment.CenterVertically) {
                when (summary.outcome) {
                    OutcomeKind.RECEIVED -> {
                        summary.statusCode?.let { StatusBadge(it) }
                    }

                    OutcomeKind.FAILED -> {
                        Text(
                            stringResource(R.string.history_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    OutcomeKind.CANCELLED -> {
                        Text(stringResource(R.string.history_cancelled), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(formatDuration(summary.duration), style = MaterialTheme.typography.bodySmall)
                summary.size?.let { Text(formatFileSize(context, it), style = MaterialTheme.typography.bodySmall) }
                Text(relativeTime(summary.sentAt), style = MaterialTheme.typography.bodySmall)
            }
        },
        colors = ListItemDefaults.colors(),
    )
}

/** "请求记录"；按接口筛选时在下方注明接口。 */
@Composable
private fun HistoryTitle(operation: OperationKey?) {
    Column {
        Text(stringResource(R.string.history_title))
        operation?.let {
            Text(
                "${it.method} ${it.path}",
                style = MaterialTheme.typography.codeMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 记录详情（PRD 7.2）：与"响应" Tab 相同，可重放与恢复到当前参数。 */
@Composable
fun HistoryEntryScreen(
    viewModel: HistoryEntryViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveFailure by viewModel.saveFailure.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restoredMessage = stringResource(R.string.history_restored)
    val actions =
        remember(viewModel) { ResponseDetailActions(viewModel::toggle, viewModel::setQuery, viewModel::setDisplay, viewModel::saveBody) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { state.detail?.entry?.let { OperationTitle(it.operation) } },
                navigationIcon = {
                    if (showBack) {
                        BackButton(onClick = onBack)
                    }
                },
            )
        },
    ) { innerPadding ->
        val detail = state.detail
        Column(Modifier.padding(innerPadding)) {
            when {
                state.missing -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(painterResource(R.drawable.ic_history), stringResource(R.string.history_entry_missing), body = null)
                    }
                }

                detail == null -> {
                    LoadingState()
                }

                else -> {
                    if (state.sending) SendingBanner(onCancel = viewModel::cancel)
                    FlowRow(
                        modifier = Modifier.padding(horizontal = ChipPadding),
                        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
                    ) {
                        ActionChip(R.drawable.ic_replay, R.string.response_replay, enabled = !state.sending, onClick = viewModel::replay)
                        ActionChip(
                            icon = R.drawable.ic_undo,
                            label = R.string.response_restore,
                            onClick = {
                                viewModel.restore()
                                scope.launch { snackbar.showSnackbar(restoredMessage) }
                            },
                            enabled = detail.entry.input != null,
                        )
                    }
                    ResponseDetailContent(detail, actions, Modifier.weight(1f))
                }
            }
        }
    }
    saveFailure?.let { SaveFailureDialog(it, viewModel::dismissSaveFailure) }
}

/** 双栏布局下右侧未选择记录时的占位。 */
@Composable
fun NoHistoryEntrySelectedPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(painterResource(R.drawable.ic_history), stringResource(R.string.history_no_entry_selected), body = null)
    }
}

private val RowSpacing = 8.dp
private val ChipPadding = 16.dp
