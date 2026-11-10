package work.foofish.apinspector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.ui.theme.codeMedium

/** 顶栏标题：方法徽章与路径。 */
@Composable
fun OperationTitle(
    operation: OperationKey,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TitleSpacing), verticalAlignment = Alignment.CenterVertically) {
        MethodBadge(operation.method)
        Text(operation.path, style = MaterialTheme.typography.codeMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val TitleSpacing = 8.dp
