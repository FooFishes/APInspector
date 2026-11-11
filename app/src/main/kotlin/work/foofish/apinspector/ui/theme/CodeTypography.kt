package work.foofish.apinspector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import work.foofish.apinspector.R

// 路径、字段名、JSON 等代码内容使用等宽字体（PRD 1.1）：JetBrains Mono（SIL OFL 1.1，可变字重字体）。

private fun jetBrainsMono(weight: FontWeight): Font =
    Font(
        resId = R.font.jetbrains_mono,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

/** 代码内容的字体族。 */
val CodeFontFamily: FontFamily =
    FontFamily(
        jetBrainsMono(FontWeight.Normal),
        jetBrainsMono(FontWeight.Medium),
        jetBrainsMono(FontWeight.SemiBold),
        jetBrainsMono(FontWeight.Bold),
    )

// 关闭编程连字：调试工具必须逐字符如实显示内容，`!=`、`->`、`www` 等不能被合成一个字形。
private const val NO_LIGATURES = "\"calt\" 0, \"liga\" 0"

private fun TextStyle.asCode(): TextStyle = copy(fontFamily = CodeFontFamily, fontFeatureSettings = NO_LIGATURES)

val Typography.codeMedium: TextStyle get() = bodyMedium.asCode()

val Typography.codeSmall: TextStyle get() = bodySmall.asCode()

val Typography.codeLabel: TextStyle get() = labelMedium.asCode()
