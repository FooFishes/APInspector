package work.foofish.apinspector.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.R
import work.foofish.apinspector.ui.component.FieldShape
import work.foofish.apinspector.ui.component.fieldColors
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall

@Composable
fun UrlImportDialog(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = rememberTextFieldState()
    val canImport = url.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_link), contentDescription = null) },
        title = { Text(stringResource(R.string.import_from_url)) },
        text = {
            OutlinedTextField(
                shape = FieldShape,
                colors = fieldColors(),
                state = url,
                label = { Text(stringResource(R.string.import_url_label)) },
                textStyle = MaterialTheme.typography.codeMedium,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                onKeyboardAction = { if (canImport) onImport(url.text.toString()) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onImport(url.text.toString()) }, enabled = canImport) {
                Text(stringResource(R.string.action_import))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun TextImportSheet(
    onImport: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // 只允许隐藏与完全展开：粘贴长文本需要足够的编辑空间。
        sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = SheetPadding).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(SheetSpacing),
        ) {
            Text(stringResource(R.string.import_text_title), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                shape = FieldShape,
                colors = fieldColors(),
                state = text,
                modifier = Modifier.fillMaxWidth().height(EditorHeight),
                placeholder = { Text(stringResource(R.string.import_text_placeholder)) },
                textStyle = MaterialTheme.typography.codeSmall,
            )
            Button(
                onClick = { onImport(text.text.toString()) },
                modifier = Modifier.align(Alignment.End).padding(bottom = SheetPadding),
                enabled = text.text.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_import))
            }
        }
    }
}

private val SheetPadding = 24.dp
private val SheetSpacing = 16.dp
private val EditorHeight = 320.dp
