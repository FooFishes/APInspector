package work.foofish.apinspector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import work.foofish.apinspector.openapi.HttpMethod

/** HTTP 方法徽章的配色：容器色与其上的内容色。 */
@Immutable
data class MethodColors(
    val container: Color,
    val content: Color,
)

/**
 * 标准方法使用固定的语义色相（GET 绿、POST 蓝、PUT 橙、PATCH 紫、DELETE 红、QUERY 青），
 * 便于在列表中一眼区分；其余方法跟随动态配色的中性色。
 */
@Composable
fun methodColors(method: HttpMethod): MethodColors {
    // 以当前配色本身判断深浅，与主题保持一致（而不是读取系统设置）。
    val dark = MaterialTheme.colorScheme.surface.luminance() < HALF_LUMINANCE
    val palette = if (dark) DarkMethodPalette else LightMethodPalette
    return palette[method] ?: MethodColors(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val HALF_LUMINANCE = 0.5f

private val LightMethodPalette: Map<HttpMethod, MethodColors> =
    mapOf(
        HttpMethod.GET to MethodColors(Color(0xFFC8F0D0), Color(0xFF0B5D1E)),
        HttpMethod.POST to MethodColors(Color(0xFFD3E3FD), Color(0xFF0B3E8F)),
        HttpMethod.PUT to MethodColors(Color(0xFFFFE0B2), Color(0xFF7A3E00)),
        HttpMethod.PATCH to MethodColors(Color(0xFFE8DDFF), Color(0xFF4A2A8C)),
        HttpMethod.DELETE to MethodColors(Color(0xFFFFDAD6), Color(0xFF8C1D18)),
        HttpMethod.QUERY to MethodColors(Color(0xFFC6F0EC), Color(0xFF00504A)),
    )

private val DarkMethodPalette: Map<HttpMethod, MethodColors> =
    mapOf(
        HttpMethod.GET to MethodColors(Color(0xFF1E4D2B), Color(0xFFA6E8B5)),
        HttpMethod.POST to MethodColors(Color(0xFF1F3A68), Color(0xFFB8CFFF)),
        HttpMethod.PUT to MethodColors(Color(0xFF5A3500), Color(0xFFFFCF94)),
        HttpMethod.PATCH to MethodColors(Color(0xFF3E2A66), Color(0xFFD7C6FF)),
        HttpMethod.DELETE to MethodColors(Color(0xFF690005), Color(0xFFFFB4AB)),
        HttpMethod.QUERY to MethodColors(Color(0xFF00433E), Color(0xFF9CEFE6)),
    )
