package work.foofish.apinspector.response.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.Variables
import work.foofish.apinspector.response.SetVariable
import work.foofish.apinspector.ui.component.fieldColors
import work.foofish.apinspector.ui.theme.codeSmall

/**
 * "设为变量…"（PRD 5、7.2）：把响应里的一个值写入变量，可以顺便建一条提取规则，以后每次 2xx 响应后自动更新。
 *
 * @param path 该值的 JSONPath。
 */
@Composable
internal fun SetVariableDialog(
    path: String,
    value: String,
    onConfirm: (SetVariable) -> Unit,
    onDismiss: () -> Unit,
) {
    val name = rememberTextFieldState()
    var addRule by rememberSaveable { mutableStateOf(true) }
    val text = name.text.toString().trim()
    val invalid = text.isNotEmpty() && !Variables.isValidName(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_variable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DialogSpacing)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.codeSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = VALUE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    state = name,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.set_variable_name)) },
                    supportingText = if (invalid) ({ Text(stringResource(R.string.set_variable_invalid)) }) else null,
                    isError = invalid,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = MaterialTheme.shapes.medium,
                    colors = fieldColors(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().toggleable(addRule, role = Role.Checkbox, onValueChange = { addRule = it }),
                    horizontalArrangement = Arrangement.spacedBy(DialogSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = addRule, onCheckedChange = null)
                    Text(stringResource(R.string.set_variable_add_rule, path), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(SetVariable(text, value, path, addRule)) }, enabled = text.isNotEmpty() && !invalid) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "设为变量…"没有写成：所属的 API 文档没有任何环境。 */
@Composable
fun VariableFailureDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_variable_title)) },
        text = { Text(stringResource(R.string.set_variable_failed)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

private const val VALUE_MAX_LINES = 3
private val DialogSpacing = 12.dp
