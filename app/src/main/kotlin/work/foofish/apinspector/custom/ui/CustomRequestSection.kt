package work.foofish.apinspector.custom.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestItem

/**
 * 接口列表顶部的"自定义请求"分区（PRD 3）：标题右侧是"＋"，没有自定义请求时只有标题一行。
 */
fun LazyListScope.customRequestSection(
    requests: ImmutableList<CustomRequestItem>,
    selected: CustomRequestId?,
    host: CustomRequestsHost,
    onOpen: (CustomRequestId) -> Unit,
) {
    item(key = SECTION_KEY) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = HeaderStartPadding, end = HeaderEndPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.custom_section_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AddCustomRequestMenu(host)
        }
    }
    customRequestItems(requests, selected, movable = false, host = host, onOpen = onOpen)
}

/** 搜索接口时自定义请求一并按名称与地址过滤。 */
fun List<CustomRequestItem>.matching(query: String): ImmutableList<CustomRequestItem> {
    val text = query.trim()
    return filter { text.isEmpty() || it.name.contains(text, ignoreCase = true) || it.url.contains(text, ignoreCase = true) }
        .toImmutableList()
}

private const val SECTION_KEY = "custom-section"
private val HeaderStartPadding = 20.dp
private val HeaderEndPadding = 8.dp
