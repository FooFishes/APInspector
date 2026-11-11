package work.foofish.apinspector.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape

// 全应用输入框的统一外观：圆角更大，未聚焦时描边退到装饰色，
// 让一屏里的许多输入框不抢内容的注意力；聚焦的那一个仍以主色描边突出。

/** 输入框的形状。 */
val FieldShape: Shape
    @Composable get() = MaterialTheme.shapes.medium

/** 输入框的配色。 */
@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
