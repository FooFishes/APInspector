package work.foofish.apinspector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// 路径、字段名、JSON 等代码内容使用等宽字体（PRD 1.1）。

val Typography.codeMedium: TextStyle get() = bodyMedium.copy(fontFamily = FontFamily.Monospace)

val Typography.codeSmall: TextStyle get() = bodySmall.copy(fontFamily = FontFamily.Monospace)

val Typography.codeLabel: TextStyle get() = labelMedium.copy(fontFamily = FontFamily.Monospace)
