package work.foofish.apinspector.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList

/**
 * 单选的连接式按钮组（M3 Expressive 中取代分段按钮）：选中项变为全圆角的主色胶囊，按下时相邻圆角收紧。
 *
 * @param fill 为 `true` 时各项等分可用宽度；否则按内容宽度排列。
 * @param isEnabled 某个选项当前是否可选。
 */
@Composable
fun <T> ChoiceGroup(
    options: ImmutableList<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    isEnabled: (T) -> Boolean = { true },
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { index, option ->
            ChoiceButton(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                shapes = connectedShapes(index, options.size),
                enabled = isEnabled(option),
                modifier = if (fill) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

@Composable
private fun RowScope.ChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    shapes: ToggleButtonShapes,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = selected,
        // 单选：点已选中的项不会取消选中。
        onCheckedChange = { if (it) onClick() },
        modifier = modifier.semantics { role = Role.RadioButton },
        enabled = enabled,
        shapes = shapes,
        colors = ToggleButtonDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        // 中文可以在任意两字之间断行；不限制的话按钮被挤窄时标签会折成两行。
        Text(text, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun connectedShapes(
    index: Int,
    count: Int,
): ToggleButtonShapes =
    when (index) {
        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }
