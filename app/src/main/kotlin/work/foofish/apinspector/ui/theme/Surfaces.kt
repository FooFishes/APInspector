package work.foofish.apinspector.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 全应用统一的两层表面（M3 Expressive 的分组列表观感）：
// 页面铺一层略深的底色，内容放在其上更亮的圆角卡片里，靠色调而不是阴影或分隔线区分层次。

/** 页面底色。 */
val ColorScheme.screen: Color get() = surfaceContainer

/** 页面上的卡片、分组列表项的底色。 */
val ColorScheme.card: Color get() = surfaceBright

/** 卡片里再嵌一层的底色（代码块、输入区）。 */
val ColorScheme.cardInset: Color get() = surfaceContainerLow

/** 顶栏与页面同色，滚动时不变色：层次由卡片表达，顶栏不再需要靠变色与内容区分。 */
@Composable
fun screenTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.screen,
        scrolledContainerColor = MaterialTheme.colorScheme.screen,
    )

/** 页面左右留白。 */
val ScreenPadding = 16.dp

/** 卡片内边距。 */
val CardPadding = 16.dp
