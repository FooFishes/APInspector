package work.foofish.apinspector.response.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.foofish.apinspector.R
import work.foofish.apinspector.response.ResponseContent
import work.foofish.apinspector.response.ResponseViewModel
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState

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
            ResponseDetailActions(viewModel::toggle, viewModel::setQuery, viewModel::setDisplay, viewModel::saveBody)
        }
    Column(modifier.fillMaxSize()) {
        state.sendingSince?.let { SendingBanner(onCancel = viewModel::cancel) }
        when (val content = state.content) {
            ResponseContent.Loading -> {
                LoadingState(Modifier.weight(1f))
            }

            ResponseContent.Empty -> {
                EmptyState(
                    painterResource(R.drawable.ic_send),
                    stringResource(R.string.response_empty_title),
                    stringResource(R.string.response_empty_body),
                    Modifier.weight(1f).fillMaxWidth(),
                )
            }

            is ResponseContent.Ready -> {
                FlowRow(
                    modifier = Modifier.padding(horizontal = DetailPadding),
                    horizontalArrangement = Arrangement.spacedBy(DetailSpacing),
                ) {
                    ActionChip(
                        R.drawable.ic_replay,
                        R.string.response_replay,
                        enabled = state.sendingSince == null,
                        onClick = viewModel::replay,
                    )
                    ActionChip(
                        R.drawable.ic_undo,
                        R.string.response_restore,
                        enabled = content.detail.entry.input != null,
                        onClick = viewModel::restore,
                    )
                    ActionChip(R.drawable.ic_history, R.string.response_all_records, onClick = onOpenHistory)
                }
                ResponseDetailContent(content.detail, actions, Modifier.weight(1f))
            }
        }
    }
    saveFailure?.let { SaveFailureDialog(it, viewModel::dismissSaveFailure) }
}

/** 请求进行中（PRD 6）：波浪进度条与"取消"。 */
@Composable
internal fun SendingBanner(onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal = DetailPadding), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.response_sending), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
internal fun ActionChip(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
