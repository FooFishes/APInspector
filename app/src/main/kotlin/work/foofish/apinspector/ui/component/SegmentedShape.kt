package work.foofish.apinspector.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive 分段列表中第 [index] 项（共 [count] 项）的形状：
 * 整组的首尾使用大圆角，组内相邻项之间使用小圆角。
 */
fun segmentedShape(
    index: Int,
    count: Int,
): Shape {
    val top = if (index == 0) OuterCorner else InnerCorner
    val bottom = if (index == count - 1) OuterCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** 分段列表中相邻项之间的间隙。 */
val SegmentGap = 2.dp

private val OuterCorner = 20.dp
private val InnerCorner = 4.dp
