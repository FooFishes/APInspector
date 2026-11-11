package work.foofish.apinspector.response.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.foofish.apinspector.R
import work.foofish.apinspector.response.ResponseContent
import work.foofish.apinspector.response.ResponseViewModel
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.theme.card

/**
 * "响应" Tab（PRD 3）：进行中显示进度与"取消"；之后显示最近一条记录的响应详情，可重放、恢复到当前参数或查看全部记录。
 *
 * @param onRestore 恢复到当前参数后切到"请求" Tab。
 */
@Composable
fun ResponseTab(
    viewModel: ResponseViewModel,
    onRestore: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val saveFailure by viewModel.saveFailure.collectAsStateWithLifecycle()
    val currentOnRestore by rememberUpdatedState(onRestore)
    LaunchedEffect(state.restored) {
        if (state.restored) {
            viewModel.onRestoredShown()
            currentOnRestore()
        }
    }
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
    Column(modifier.fillMaxSize()) {
        state.sendingSince?.let { SendingBanner(onCancel = viewModel::cancel) }
        when (val content = state.content) {
            ResponseContent.Loading -> {
                LoadingState(Modifier.weight(1f))
            }

            ResponseContent.Empty -> {
                // 第一次请求还在进行时，上面的横幅已经说明了状态，不再同时显示"还没有发送过请求"。
                if (state.sendingSince == null) {
                    EmptyState(
                        painterResource(R.drawable.ic_send),
                        stringResource(R.string.response_empty_title),
                        stringResource(R.string.response_empty_body),
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }

            is ResponseContent.Ready -> {
                ResponseDetailContent(content.detail, actions, Modifier.weight(1f)) {
                    ReplayButton(enabled = state.sendingSince == null, onClick = viewModel::replay)
                    MoreMenu(
                        canRestore = content.detail.entry.input != null,
                        onRestore = viewModel::restore,
                        onOpenHistory = onOpenHistory,
                    )
                }
            }
        }
    }
    saveFailure?.let { SaveFailureDialog(it, viewModel::dismissSaveFailure) }
    val variableFailed by viewModel.variableFailed.collectAsStateWithLifecycle()
    if (variableFailed) VariableFailureDialog(viewModel::onVariableFailureShown)
}

/** 请求进行中（PRD 6）：波浪进度条与"取消"。 */
@Composable
internal fun SendingBanner(onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = DetailPadding, vertical = DetailSpacing),
            horizontalArrangement = Arrangement.spacedBy(DetailSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(SendingIndicatorSize))
            Text(stringResource(R.string.response_sending), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            FilledTonalButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** 重放：这一页最常用的操作，直接放在结果摘要上。 */
@Composable
internal fun ReplayButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
        Icon(painterResource(R.drawable.ic_replay), contentDescription = stringResource(R.string.response_replay))
    }
}

/** 不常用的操作收进菜单：恢复到当前参数、查看该接口的全部记录。 */
@Composable
private fun MoreMenu(
    canRestore: Boolean,
    onRestore: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.response_restore)) },
                onClick = {
                    expanded = false
                    onRestore()
                },
                enabled = canRestore,
                leadingIcon = { Icon(painterResource(R.drawable.ic_undo), contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.response_all_records)) },
                onClick = {
                    expanded = false
                    onOpenHistory()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
            )
        }
    }
}

private val SendingIndicatorSize = 32.dp
