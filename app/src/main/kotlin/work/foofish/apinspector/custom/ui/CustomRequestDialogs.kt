package work.foofish.apinspector.custom.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CurlImportResult
import work.foofish.apinspector.custom.MoveTarget
import work.foofish.apinspector.openapi.request.CurlError
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.ui.component.TextInputDialog
import work.foofish.apinspector.ui.component.fieldColors
import work.foofish.apinspector.ui.theme.codeSmall

/** 自定义请求列表的对话框：cURL 导入、重命名、移动、删除。 */
@Composable
fun CustomRequestDialogs(
    host: CustomRequestsHost,
    moveTargets: ImmutableList<MoveTarget>,
) {
    val dismiss = { host.dialog = null }
    when (val dialog = host.dialog) {
        null -> {}

        is CustomDialog.Import -> {
            CurlImportDialog(dialog.failure, onImport = host::import, onDismiss = dismiss)
        }

        is CustomDialog.Ignored -> {
            MessageDialog(
                title = stringResource(R.string.curl_ignored_title),
                body = stringResource(R.string.curl_ignored_body, dialog.arguments.joinToString(ARGUMENT_SEPARATOR)),
                onDismiss = { host.open(dialog.id) },
            )
        }

        is CustomDialog.Rename -> {
            TextInputDialog(
                title = stringResource(R.string.custom_rename),
                label = stringResource(R.string.request_name),
                confirmText = stringResource(R.string.action_ok),
                onConfirm = { name ->
                    host.actions.rename(dialog.item.id, name)
                    dismiss()
                },
                onDismiss = dismiss,
                initial = dialog.item.name,
            )
        }

        is CustomDialog.Move -> {
            MoveDialog(
                targets = moveTargets,
                onMove = { target ->
                    host.actions.move(dialog.item.id, target)
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }

        is CustomDialog.Delete -> {
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.custom_delete_title, dialog.item.name)) },
                text = { Text(stringResource(R.string.custom_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            host.actions.delete(dialog.item.id)
                            dismiss()
                        },
                    ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** 粘贴 cURL 命令（PRD 4.2）；解析失败时在输入框下说明原因，内容保留。 */
@Composable
private fun CurlImportDialog(
    failure: CurlImportResult.Failed?,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_import_curl)) },
        text = {
            OutlinedTextField(
                state = state,
                modifier = Modifier.fillMaxWidth().heightIn(min = CurlFieldMinHeight, max = CurlFieldMaxHeight),
                textStyle = MaterialTheme.typography.codeSmall,
                label = { Text(stringResource(R.string.curl_label)) },
                supportingText = failure?.let { { Text(stringResource(it.error.message())) } },
                isError = failure != null,
                lineLimits = TextFieldLineLimits.MultiLine(),
                shape = MaterialTheme.shapes.medium,
                colors = fieldColors(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(state.text.toString()) }, enabled = state.text.isNotBlank()) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun CurlError.message(): Int =
    when (this) {
        CurlError.NOT_CURL -> R.string.curl_error_not_curl
        CurlError.NO_URL -> R.string.curl_error_no_url
        CurlError.UNTERMINATED_QUOTE -> R.string.curl_error_quote
        CurlError.UNKNOWN_METHOD -> R.string.curl_error_method
    }

/** 选择移入的 API 文档；移动是单向的，临时请求自己的变量随之丢弃（PRD 4.2）。 */
@Composable
private fun MoveDialog(
    targets: ImmutableList<MoveTarget>,
    onMove: (SpecId) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_move_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MoveSpacing)) {
                Text(stringResource(if (targets.isEmpty()) R.string.custom_move_no_spec else R.string.custom_move_hint))
                targets.forEach { target ->
                    Text(
                        text = target.title,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onMove(target.id) }
                                .padding(horizontal = MoveItemPaddingHorizontal, vertical = MoveItemPaddingVertical),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MessageDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private const val ARGUMENT_SEPARATOR = "  "
private val CurlFieldMinHeight = 160.dp
private val CurlFieldMaxHeight = 320.dp
private val MoveSpacing = 4.dp
private val MoveItemPaddingHorizontal = 12.dp
private val MoveItemPaddingVertical = 12.dp
