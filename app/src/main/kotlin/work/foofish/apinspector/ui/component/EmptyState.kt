package work.foofish.apinspector.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 内容为空时的占位：Expressive 形状衬底上的图标，加标题与说明。
 *
 * 放在居中的容器中使用；内容不超出可用高度时自适应大小。
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        // 高度不足（如横屏双栏）时可滚动，避免内容被裁切。
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(ContentPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(BadgeSize)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(IconSize),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
        )
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val ContentPadding = 24.dp
private val Spacing = 16.dp
private val BadgeSize = 96.dp
private val IconSize = 40.dp
