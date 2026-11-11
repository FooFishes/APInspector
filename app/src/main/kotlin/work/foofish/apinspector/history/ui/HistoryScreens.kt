package work.foofish.apinspector.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.HistoryEntryId
import work.foofish.apinspector.history.HistorySummary
import work.foofish.apinspector.history.OutcomeKind
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.response.ui.ReplayButton
import work.foofish.apinspector.response.ui.ResponseDetailActions
import work.foofish.apinspector.response.ui.ResponseDetailContent
import work.foofish.apinspector.response.ui.SaveFailureDialog
import work.foofish.apinspector.response.ui.SendingBanner
import work.foofish.apinspector.response.ui.StatusBadge
import work.foofish.apinspector.response.ui.VariableFailureDialog
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.ConfirmDialog
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.OperationTitle
import work.foofish.apinspector.ui.text.dayLabel
import work.foofish.apinspector.ui.text.formatDuration
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.text.formatTime
import work.foofish.apinspector.ui.text.relativeTime
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors
import java.time.LocalDate
import java.time.ZoneId

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
        containerColor = MaterialTheme.colorScheme.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                // 按接口筛选时在标题下注明接口。
                subtitle = { state.operation?.let { OperationSubtitle(it) } },
                navigationIcon = {
                    BackButton(onClick = onBack)
                },
                colors = screenTopAppBarColors(),
                actions = {
                    if (state.single) {
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
                HistoryList(
                    entries = entries,
                    selected = selected,
                    singleOperation = state.single,
                    onOpen = onOpen,
                    topPadding = innerPadding.calculateTopPadding(),
                )
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
            destructive = true,
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
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

/**
 * 记录按天分组，日期标题吸顶：翻很久以前的记录时始终知道自己在哪一天。
 *
 * @param singleOperation 列表只含一个接口的记录；此时每行不再重复方法与路径。
 */
@Composable
private fun HistoryList(
    entries: ImmutableList<HistorySummary>,
    selected: HistoryEntryId?,
    singleOperation: Boolean,
    onOpen: (HistoryEntryId) -> Unit,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(entries, zone) { entries.groupBy { it.sentAt.atZone(zone).toLocalDate() } }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = topPadding, bottom = ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        days.forEach { (day, summaries) ->
            stickyHeader(key = day.toEpochDay()) { DayHeader(day) }
            itemsIndexed(summaries, key = { _, summary -> summary.id.value }) { index, summary ->
                HistoryRow(
                    summary = summary,
                    shapes = ListItemDefaults.segmentedShapes(index, summaries.size),
                    selected = summary.id == selected,
                    singleOperation = singleOperation,
                    onClick = { onOpen(summary.id) },
                    modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: LocalDate,
    modifier: Modifier = Modifier,
) {
    Text(
        text = dayLabel(day),
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.screen)
                .padding(horizontal = DayHeaderPaddingHorizontal, vertical = DayHeaderPaddingVertical),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * 一条记录。
 *
 * 全部接口的列表里：方法徽章、路径，下面是时刻、耗时与大小，行尾是结果。
 * 只看一个接口时方法与路径每行都一样，改为结果在前、时刻作标题，一眼扫过去看的就是"哪次成功、哪次失败"。
 */
@Composable
private fun HistoryRow(
    summary: HistorySummary,
    shapes: ListItemShapes,
    selected: Boolean,
    singleOperation: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val time = formatTime(summary.sentAt)
    val facts =
        listOfNotNull(
            time.takeUnless { singleOperation },
            formatDuration(summary.duration),
            summary.size?.let { formatFileSize(context, it) },
        ).joinToString(stringResource(R.string.spec_info_separator))
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        leadingContent = {
            if (singleOperation) {
                Box(Modifier.widthIn(min = OutcomeSlotWidth), contentAlignment = Alignment.Center) { OutcomeMark(summary) }
            } else {
                MethodBadge(summary.method)
            }
        },
        content = {
            Text(
                text = if (singleOperation) time else summary.path,
                style = MaterialTheme.typography.codeMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(facts, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (singleOperation) null else ({ OutcomeMark(summary) }),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.card),
    )
}

/** 行尾的结果：状态码徽章，或"失败" / "已取消"。 */
@Composable
private fun OutcomeMark(summary: HistorySummary) {
    when (summary.outcome) {
        OutcomeKind.RECEIVED -> {
            summary.statusCode?.let { StatusBadge(it) }
        }

        OutcomeKind.FAILED -> {
            Text(
                stringResource(R.string.history_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
        }

        OutcomeKind.CANCELLED -> {
            Text(
                stringResource(R.string.history_cancelled),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun OperationSubtitle(operation: OperationKey) {
    Text(
        "${operation.method} ${operation.path}",
        style = MaterialTheme.typography.codeSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
        remember(viewModel) {
            ResponseDetailActions(
                viewModel::toggle,
                viewModel::setQuery,
                viewModel::setDisplay,
                viewModel::saveBody,
                viewModel::setVariable,
            )
        }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.screen,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { EntryTopBar(state.detail?.entry, showBack, onBack) },
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
                    ResponseDetailContent(detail, actions, Modifier.weight(1f)) {
                        ReplayButton(enabled = !state.sending, onClick = viewModel::replay)
                        IconButton(
                            onClick = {
                                viewModel.restore()
                                scope.launch { snackbar.showSnackbar(restoredMessage) }
                            },
                            enabled = detail.entry.input != null,
                        ) {
                            Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.response_restore))
                        }
                    }
                }
            }
        }
    }
    saveFailure?.let { SaveFailureDialog(it, viewModel::dismissSaveFailure) }
    val variableFailed by viewModel.variableFailed.collectAsStateWithLifecycle()
    if (variableFailed) VariableFailureDialog(viewModel::onVariableFailureShown)
}

@Composable
private fun EntryTopBar(
    entry: HistoryEntry?,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { entry?.let { OperationTitle(it.method, it.path) } },
        navigationIcon = {
            if (showBack) {
                BackButton(onClick = onBack)
            }
        },
        colors = screenTopAppBarColors(),
    )
}

/** 双栏布局下右侧未选择记录时的占位。 */
@Composable
fun NoHistoryEntrySelectedPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(painterResource(R.drawable.ic_history), stringResource(R.string.history_no_entry_selected), body = null)
    }
}

private val RowSpacing = 8.dp
private val DayHeaderPaddingHorizontal = 24.dp
private val DayHeaderPaddingVertical = 8.dp
private val OutcomeSlotWidth = 48.dp
private val ListBottomPadding = 24.dp
