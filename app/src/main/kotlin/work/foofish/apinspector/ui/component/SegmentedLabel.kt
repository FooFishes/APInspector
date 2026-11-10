package work.foofish.apinspector.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 分段按钮的标签：不换行。
 *
 * 分段按钮行按标签的最小固有宽度分配宽度，中文可以在任意两字之间断行，
 * 最小宽度只有一个字，按钮会被挤窄、文字折行。
 */
@Composable
fun SegmentedLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, maxLines = 1, softWrap = false)
}
