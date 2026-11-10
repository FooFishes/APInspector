package work.foofish.apinspector.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.OperationDiff
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.ui.theme.codeSmall

/** 新文档中有接口消失时，在删除它们的数据前确认（PRD 2、ADR-0001）。 */
@Composable
fun ConfirmRemovalDialog(
    removed: ImmutableList<OperationKey>,
    withPresets: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
        title = { Text(pluralStringResource(R.plurals.refresh_confirm_title, removed.size, removed.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
                Text(stringResource(R.string.refresh_confirm_body))
                removed.take(MAX_LISTED).forEach { Text(it.toString(), style = MaterialTheme.typography.codeSmall) }
                val hidden = removed.size - MAX_LISTED
                if (hidden > 0) Text(pluralStringResource(R.plurals.refresh_confirm_more, hidden, hidden))
                if (withPresets > 0) Text(pluralStringResource(R.plurals.refresh_confirm_presets, withPresets, withPresets))
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.refresh_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** 刷新后的变更摘要（PRD 2）。 */
@Composable
fun RefreshSummaryDialog(
    diff: OperationDiff,
    warningCount: Int,
    serversChanged: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
        title = { Text(stringResource(R.string.refresh_done_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
                Text(
                    if (diff.isEmpty) {
                        stringResource(R.string.refresh_no_changes)
                    } else {
                        stringResource(R.string.refresh_summary, diff.added.size, diff.removed.size, diff.modified.size)
                    },
                )
                if (warningCount > 0) Text(pluralStringResource(R.plurals.refresh_warnings, warningCount, warningCount))
                if (serversChanged) Text(stringResource(R.string.refresh_servers_changed))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private const val MAX_LISTED = 8
private val LineSpacing = 4.dp
