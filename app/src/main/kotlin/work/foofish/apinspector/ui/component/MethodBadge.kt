package work.foofish.apinspector.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.openapi.HttpMethod
import work.foofish.apinspector.ui.theme.codeLabel
import work.foofish.apinspector.ui.theme.methodColors

/** 按方法着色的徽章；固定最小宽度，使列表中的路径对齐。 */
@Composable
fun MethodBadge(
    method: HttpMethod,
    modifier: Modifier = Modifier,
) {
    val colors = methodColors(method)
    Surface(
        modifier = modifier.widthIn(min = MinWidth),
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Text(
            text = method.name,
            modifier = Modifier.padding(horizontal = HorizontalPadding, vertical = VerticalPadding),
            style = MaterialTheme.typography.codeLabel,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private val MinWidth = 64.dp
private val HorizontalPadding = 8.dp
private val VerticalPadding = 4.dp
