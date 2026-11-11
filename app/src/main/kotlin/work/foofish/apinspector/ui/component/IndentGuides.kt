package work.foofish.apinspector.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 树形列表的层级引导线：每一级祖先在本行画一条贯穿整行高度的细竖线。
 * 相邻的行上下紧贴时，各行的线首尾相接，连成从父节点垂下来的一条线。
 *
 * @param depth 本行的深度；根为 0，不画线。
 * @param step 每一级的缩进。
 * @param offset 第一条线距行左缘的距离（通常是折叠箭头的中心）。
 */
fun Modifier.indentGuides(
    depth: Int,
    step: Dp,
    offset: Dp,
    color: Color,
): Modifier =
    if (depth <= 0) {
        this
    } else {
        drawBehind {
            val stroke = GuideWidth.toPx()
            repeat(depth) { level ->
                val x = (step * level + offset).toPx()
                drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }
    }

private val GuideWidth = 1.dp
