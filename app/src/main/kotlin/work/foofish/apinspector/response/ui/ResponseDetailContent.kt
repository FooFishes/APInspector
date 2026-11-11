package work.foofish.apinspector.response.ui

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.ReceivedResponse
import work.foofish.apinspector.openapi.request.Field
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.HttpPart
import work.foofish.apinspector.response.BodyDisplay
import work.foofish.apinspector.response.ResponseDetail
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.SectionCard
import work.foofish.apinspector.ui.text.formatDuration
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.text.reasonPhrase
import work.foofish.apinspector.ui.theme.CodeFontFamily
import work.foofish.apinspector.ui.theme.codeLabel
import work.foofish.apinspector.ui.theme.codeSmall
import work.foofish.apinspector.ui.theme.screen
import java.io.File

/** 响应详情的 Tab（PRD 7.2）：按使用频率排列，最常看的 Body 在最前。 */
private enum class DetailTab(
    val title: Int,
) {
    BODY(R.string.detail_body),
    HEADERS(R.string.detail_headers),
    REQUEST(R.string.detail_request),
    OVERVIEW(R.string.detail_overview),
    EXPORT(R.string.detail_export),
}

/** 响应详情的操作。 */
class ResponseDetailActions(
    val onToggle: (String) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onDisplayChange: (BodyDisplay) -> Unit,
    val onSaveBody: (File, Uri) -> Unit,
)

/**
 * 响应详情（PRD 7.2）：顶部为结果摘要，其下为 Body / 响应头 / 请求 / 概览 / 导出。
 * 收到响应时默认显示 Body，否则显示概览（失败原因在那里）。
 *
 * @param summaryActions 放在结果摘要右侧的操作（重放等）。
 */
@Composable
fun ResponseDetailContent(
    detail: ResponseDetail,
    actions: ResponseDetailActions,
    modifier: Modifier = Modifier,
    summaryActions: @Composable RowScope.() -> Unit = {},
) {
    val response = (detail.entry.outcome as? Outcome.Received)?.response
    var tab by rememberSaveable(detail.entry.id.value) { mutableStateOf(if (response != null) DetailTab.BODY else DetailTab.OVERVIEW) }
    Column(modifier = modifier.fillMaxSize()) {
        StatusSummary(detail.entry, Modifier.padding(horizontal = DetailPadding, vertical = DetailSpacing), summaryActions)
        SecondaryScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = MaterialTheme.colorScheme.screen,
            edgePadding = DetailPadding,
        ) {
            DetailTab.entries.forEach { entry ->
                val count =
                    when (entry) {
                        DetailTab.HEADERS -> response?.headers?.size
                        DetailTab.REQUEST -> detail.entry.request.headers.size
                        else -> null
                    }
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { TabLabel(stringResource(entry.title), count) })
            }
        }
        val contentModifier = Modifier.weight(1f).fillMaxWidth()
        when (tab) {
            DetailTab.BODY -> BodyTab(detail, actions, contentModifier)
            DetailTab.HEADERS -> HeadersTab(response, contentModifier)
            DetailTab.REQUEST -> RequestTab(detail.entry, contentModifier)
            DetailTab.OVERVIEW -> OverviewTab(detail.entry, contentModifier)
            DetailTab.EXPORT -> ExportTab(detail.curl, contentModifier)
        }
    }
}

/** Tab 标题；[count] 不为空且大于 0 时附一个数量角标（响应头有几个，不用点进去就知道）。 */
@Composable
private fun TabLabel(
    title: String,
    count: Int?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TabBadgeSpacing), verticalAlignment = Alignment.CenterVertically) {
        Text(title, maxLines = 1)
        if (count != null && count > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) { Text(count.toString()) }
        }
    }
}

/**
 * 结果摘要：一眼看清这次请求怎么样了。
 *
 * 整张卡片按结果着色（2xx / 3xx / 4xx / 5xx、失败、取消）：左侧是状态码与原因短语，右侧是耗时与大小，最右是 [actions]。
 * 只占一行的高度：这一页的主角是下面的 Body。
 */
@Composable
fun StatusSummary(
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val outcome = entry.outcome
    val (container, content) =
        when (outcome) {
            is Outcome.Received -> statusColors(outcome.response.statusCode)
            is Outcome.Failed -> colors.errorContainer to colors.onErrorContainer
            Outcome.Cancelled -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        }
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container, contentColor = content) {
        Row(
            modifier = Modifier.heightIn(min = SummaryMinHeight).padding(start = SummaryPaddingStart, end = SummaryPaddingEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(top = SummaryPaddingVertical, bottom = SummaryPaddingVertical, end = SummarySpacing),
                horizontalArrangement = Arrangement.spacedBy(SummarySpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (outcome) {
                    is Outcome.Received -> ReceivedHeadline(outcome.response)
                    is Outcome.Failed -> Headline(stringResource(R.string.history_failed), outcome.error.message())
                    Outcome.Cancelled -> Headline(stringResource(R.string.history_cancelled), detail = null)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDuration(entry.duration), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1)
                    (outcome as? Outcome.Received)?.response?.body?.let {
                        Text(formatFileSize(context, it.totalBytes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
            actions()
        }
    }
}

@Composable
private fun RowScope.ReceivedHeadline(response: ReceivedResponse) {
    Text(
        text = response.statusCode.toString(),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        fontFamily = CodeFontFamily,
    )
    Text(
        // HTTP/2 起没有原因短语，用标准短语补上。
        text = response.statusMessage.ifBlank { reasonPhrase(response.statusCode).orEmpty() },
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun RowScope.Headline(
    title: String,
    detail: String?,
) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun HeadersTab(
    response: ReceivedResponse?,
    modifier: Modifier = Modifier,
) {
    if (response == null || response.headers.isEmpty()) {
        CenteredMessage(stringResource(R.string.detail_no_headers), modifier)
        return
    }
    SelectionContainer(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(DetailPadding).navigationBarsPadding()) {
            SectionCard { FieldList(response.headers) }
        }
    }
}

/** 实际发出的完整请求（PRD 7.2"请求"）。 */
@Composable
private fun RequestTab(
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
) {
    val request = entry.request
    SelectionContainer(modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(DetailPadding).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(CardSpacing),
        ) {
            SectionCard {
                MethodBadge(request.method)
                Text(request.url, modifier = Modifier.padding(top = UrlTopSpacing), style = MaterialTheme.typography.codeSmall)
            }
            val body = request.body
            val headers = request.headers + listOfNotNull(body?.let { Field(CONTENT_TYPE, it.contentType()) })
            if (headers.isNotEmpty()) SectionCard { FieldList(headers) }
            when (body) {
                null -> {}

                is HttpBody.Text -> {
                    SectionCard { Text(body.text, style = MaterialTheme.typography.codeSmall) }
                }

                is HttpBody.File -> {
                    SectionCard { Muted(stringResource(R.string.detail_request_file, body.file.name)) }
                }

                is HttpBody.Multipart -> {
                    SectionCard {
                        FieldList(
                            body.parts.map { part ->
                                when (part) {
                                    is HttpPart.Text -> Field(part.name, part.value)
                                    is HttpPart.File -> Field(part.name, stringResource(R.string.detail_request_file, part.file.name))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun HttpBody.contentType(): String =
    when (this) {
        is HttpBody.Text -> contentType
        is HttpBody.Multipart -> contentType
        is HttpBody.File -> contentType
    }

/**
 * 键值列表（响应头、请求头、multipart 字段）：名称用主色小字在上，值在下。
 * 名称与值各占一行，长短不一的名称不会把值挤得参差不齐，长值换行后也仍然对齐。
 */
@Composable
private fun FieldList(fields: List<Field>) {
    Column(verticalArrangement = Arrangement.spacedBy(FieldSpacing)) {
        fields.forEach { field ->
            Column(verticalArrangement = Arrangement.spacedBy(FieldLabelSpacing)) {
                Text(field.name, style = MaterialTheme.typography.codeLabel, color = MaterialTheme.colorScheme.primary)
                Text(field.value, style = MaterialTheme.typography.codeSmall)
            }
        }
    }
}

/** 复制为 cURL（PRD 7.2"导出"）。 */
@Composable
private fun ExportTab(
    curl: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(DetailPadding).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        SectionCard { SelectionContainer { Text(curl, style = MaterialTheme.typography.codeSmall) } }
        Button(
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CURL_LABEL, curl))) } },
            modifier = Modifier.align(Alignment.End),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.detail_copy_curl), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

/** 另存 Body；文件名取 URL 的最后一段。 */
@Composable
internal fun SaveBodyButton(
    file: File,
    suggestedName: String,
    onSave: (File, Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val save = rememberSaveBody(file, onSave)
    FilledTonalButton(
        onClick = { save(suggestedName) },
        modifier = modifier,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(stringResource(R.string.detail_save_body), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing), maxLines = 1)
    }
}

/** 只有图标的"另存 Body"，用在 Body 上方的工具行里。 */
@Composable
internal fun SaveBodyIconButton(
    file: File,
    suggestedName: String,
    onSave: (File, Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val save = rememberSaveBody(file, onSave)
    FilledTonalIconButton(onClick = { save(suggestedName) }, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_download), contentDescription = stringResource(R.string.detail_save_body))
    }
}

/** 弹出系统的"创建文档"界面，选定位置后把 [file] 存过去；返回值以建议的文件名调用。 */
@Composable
private fun rememberSaveBody(
    file: File,
    onSave: (File, Uri) -> Unit,
): (String) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ANY_TYPE)) { uri -> uri?.let { onSave(file, it) } }
    return launcher::launch
}

/** 另存失败的提示。 */
@Composable
fun SaveFailureDialog(
    reason: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_save_failed)) },
        text = { Text(reason) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
    )
}

@Composable
internal fun Muted(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(DetailPadding), contentAlignment = Alignment.Center) { Muted(text) }
}

internal val DetailPadding = 16.dp
internal val DetailSpacing = 8.dp
private val CardSpacing = 12.dp
private val FieldSpacing = 12.dp
private val FieldLabelSpacing = 2.dp
private val UrlTopSpacing = 8.dp
private val TabBadgeSpacing = 6.dp
private val SummaryMinHeight = 56.dp
private val SummaryPaddingStart = 20.dp
private val SummaryPaddingEnd = 8.dp
private val SummaryPaddingVertical = 8.dp
private val SummarySpacing = 12.dp
private const val CONTENT_TYPE = "Content-Type"
private const val CURL_LABEL = "cURL"
private const val ANY_TYPE = "*/*"
