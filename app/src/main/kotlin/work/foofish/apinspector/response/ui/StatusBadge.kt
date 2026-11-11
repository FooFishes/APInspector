package work.foofish.apinspector.response.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.ui.theme.codeLabel

/** 状态码徽章（PRD 7.2）：按 2xx / 3xx / 4xx / 5xx 着色。 */
@Composable
fun StatusBadge(
    code: Int,
    modifier: Modifier = Modifier,
) {
    val (container, content) = statusColors(code)
    Surface(modifier = modifier, color = container, contentColor = content, shape = CircleShape) {
        Text(
            code.toString(),
            modifier = Modifier.padding(horizontal = BadgePaddingHorizontal, vertical = BadgePaddingVertical),
            style = MaterialTheme.typography.codeLabel,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 状态码所属类别的容器色与其上的内容色。 */
@Composable
internal fun statusColors(code: Int): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (code / STATUS_CLASS) {
        SUCCESS -> colors.tertiaryContainer to colors.onTertiaryContainer
        REDIRECT -> colors.secondaryContainer to colors.onSecondaryContainer
        CLIENT_ERROR -> colors.errorContainer to colors.onErrorContainer
        SERVER_ERROR -> colors.error to colors.onError
        else -> colors.surfaceContainerHighest to colors.onSurfaceVariant
    }
}

private const val STATUS_CLASS = 100
private const val SUCCESS = 2
private const val REDIRECT = 3
private const val CLIENT_ERROR = 4
private const val SERVER_ERROR = 5
private val BadgePaddingHorizontal = 8.dp
private val BadgePaddingVertical = 2.dp
