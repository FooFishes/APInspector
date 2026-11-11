package work.foofish.apinspector.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import work.foofish.apinspector.ui.theme.CardPadding
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.card

/** 一行在所属卡片中的位置；决定哪几个角是圆角。 */
enum class GroupPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM,
    ;

    internal val roundsTop: Boolean get() = this == SINGLE || this == TOP
    internal val roundsBottom: Boolean get() = this == SINGLE || this == BOTTOM
}

/** 共 [count] 行的卡片中第 [index] 行的位置。 */
fun groupPosition(
    index: Int,
    count: Int,
): GroupPosition =
    when {
        count <= 1 -> GroupPosition.SINGLE
        index == 0 -> GroupPosition.TOP
        index == count - 1 -> GroupPosition.BOTTOM
        else -> GroupPosition.MIDDLE
    }

/**
 * 把懒加载列表中相邻的若干行拼成一张卡片：每行各自绘制卡片底色，只有首尾行带圆角。
 *
 * 这样卡片内容仍是独立的列表项（可以单独增删、做位移动画），而不必把整张卡片作为一个巨大的列表项。
 */
@Composable
fun Modifier.groupedCard(position: GroupPosition): Modifier =
    this
        .fillMaxWidth()
        .padding(horizontal = ScreenPadding)
        .clip(groupShape(position))
        .background(MaterialTheme.colorScheme.card)

private fun groupShape(position: GroupPosition): Shape {
    val top = if (position.roundsTop) CardCorner else NoCorner
    val bottom = if (position.roundsBottom) CardCorner else NoCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * 卡片中的一行。
 *
 * @param flush 与相邻的行上下紧贴，不留行距：树形列表的行靠这一点把层级引导线连成一条。
 */
class CardRow(
    val key: Any,
    val flush: Boolean = false,
    val content: @Composable () -> Unit,
)

/**
 * 把 [rows] 作为一张卡片放进懒加载列表；首尾行在卡片边缘多留一些空白。
 *
 * @param startInset 行内容距卡片左缘的距离；左侧自带勾选框等控件的行可以收窄。
 */
fun LazyListScope.cardRows(
    rows: List<CardRow>,
    startInset: Dp = CardPadding,
) {
    itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
        val position = groupPosition(index, rows.size)
        val spacing = if (row.flush) NoSpacing else RowSpacing
        Box(
            Modifier
                .animateItem()
                .groupedCard(position)
                .padding(
                    start = startInset,
                    end = CardPadding,
                    top = if (position.roundsTop) EdgeSpacing else spacing,
                    bottom = if (position.roundsBottom) EdgeSpacing else spacing,
                ),
        ) { row.content() }
    }
}

/** 页面上的一张卡片：内容不多、不需要按行懒加载时使用。 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(CardCorner), color = MaterialTheme.colorScheme.card) {
        Column(modifier = Modifier.padding(CardPadding), content = content)
    }
}

/** 卡片上方的分区标题。 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(start = LabelInset, top = LabelTopSpacing, bottom = LabelBottomSpacing),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 卡片圆角：M3 Expressive 的 large-increased。 */
internal val CardCorner = 20.dp
private val NoCorner = 0.dp
private val RowSpacing = 6.dp
private val NoSpacing = 0.dp
private val EdgeSpacing = 12.dp
private val LabelInset = 8.dp
private val LabelTopSpacing = 20.dp
private val LabelBottomSpacing = 8.dp
