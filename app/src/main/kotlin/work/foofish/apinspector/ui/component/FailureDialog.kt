package work.foofish.apinspector.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import work.foofish.apinspector.R
import work.foofish.apinspector.spec.ImportFailure
import work.foofish.apinspector.ui.text.message

/** 导入、刷新等获取文档内容失败时的提示。 */
@Composable
fun FailureDialog(
    title: String,
    failure: ImportFailure,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_error), contentDescription = null) },
        title = { Text(title) },
        text = { Text(failure.message()) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}
