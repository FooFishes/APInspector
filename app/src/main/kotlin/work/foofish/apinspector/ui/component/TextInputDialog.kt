package work.foofish.apinspector.ui.component

import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R

/**
 * 输入一行文本（如名称）的对话框。
 *
 * @param validate 返回错误提示；`null` 表示可以确认。空白内容总是不能确认。
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initial: String = "",
    validate: (String) -> String? = { null },
) {
    val state = rememberTextFieldState(initial)
    val text = state.text.toString().trim()
    val error = if (text.isEmpty()) null else validate(text)
    val canConfirm = text.isNotEmpty() && error == null
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                shape = FieldShape,
                colors = fieldColors(),
                state = state,
                label = { Text(label) },
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                lineLimits = TextFieldLineLimits.SingleLine,
                onKeyboardAction = { if (canConfirm) onConfirm(text) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = canConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * 需要确认的操作（如删除、覆盖）。
 *
 * @param destructive 不可撤销的删除类操作：确认按钮使用错误色，并显示删除图标。
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String?,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon =
            if (destructive) {
                { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) }
            } else {
                null
            },
        title = { Text(title) },
        text = body?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    if (destructive) {
                        ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.textButtonColors()
                    },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
