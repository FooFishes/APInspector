package work.foofish.apinspector.response.ui

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.ReceivedResponse
import work.foofish.apinspector.openapi.request.HttpBody
import work.foofish.apinspector.openapi.request.HttpPart
import work.foofish.apinspector.response.BodyDisplay
import work.foofish.apinspector.response.ResponseDetail
import work.foofish.apinspector.ui.text.formatDateTime
import work.foofish.apinspector.ui.text.formatDuration
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall
import java.io.File

/** 响应详情的 Tab（PRD 7.2）。 */
private enum class DetailTab(
    val title: Int,
) {
    OVERVIEW(R.string.detail_overview),
    BODY(R.string.detail_body),
    HEADERS(R.string.detail_headers),
    REQUEST(R.string.detail_request),
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
 * 响应详情（PRD 7.2）：顶部为状态摘要，其下为概览 / Body / 响应头 / 请求 / 导出。
 * 收到响应时默认显示 Body，否则显示概览。
 */
@Composable
fun ResponseDetailContent(
    detail: ResponseDetail,
    actions: ResponseDetailActions,
    modifier: Modifier = Modifier,
) {
    val received = detail.entry.outcome is Outcome.Received
    var tab by rememberSaveable(detail.entry.id.value) { mutableStateOf(if (received) DetailTab.BODY else DetailTab.OVERVIEW) }
    Column(modifier = modifier.fillMaxSize()) {
        StatusSummary(detail.entry, Modifier.padding(horizontal = DetailPadding, vertical = DetailSpacing))
        SecondaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = DetailPadding) {
            DetailTab.entries.forEach { entry ->
                Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(stringResource(entry.title)) })
            }
        }
        val contentModifier = Modifier.weight(1f).fillMaxWidth()
        when (tab) {
            DetailTab.OVERVIEW -> OverviewTab(detail.entry, contentModifier)
            DetailTab.BODY -> BodyTab(detail, actions, contentModifier)
            DetailTab.HEADERS -> HeadersTab((detail.entry.outcome as? Outcome.Received)?.response, contentModifier)
            DetailTab.REQUEST -> RequestTab(detail.entry, contentModifier)
            DetailTab.EXPORT -> ExportTab(detail.curl, contentModifier)
        }
    }
}

/** 状态码（按 2xx / 4xx / 5xx 着色）、耗时与大小；失败或取消时显示原因。 */
@Composable
fun StatusSummary(
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DetailSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        when (val outcome = entry.outcome) {
            is Outcome.Received -> {
                StatusBadge(outcome.response.statusCode)
                Text(outcome.response.statusMessage, style = MaterialTheme.typography.bodyMedium)
            }

            is Outcome.Failed -> {
                Text(outcome.error.message(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Outcome.Cancelled -> {
                Text(
                    stringResource(R.string.history_cancelled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Muted(formatDuration(entry.duration))
        (entry.outcome as? Outcome.Received)?.response?.body?.let { Muted(formatFileSize(context, it.totalBytes)) }
    }
}

@Composable
private fun OverviewTab(
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val response = (entry.outcome as? Outcome.Received)?.response
    SelectionContainer(modifier) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(DetailPadding),
            verticalArrangement = Arrangement.spacedBy(DetailSpacing),
        ) {
            Fact(stringResource(R.string.detail_sent_at), formatDateTime(entry.sentAt))
            Fact(
                stringResource(R.string.detail_source),
                entry.presetName?.let { stringResource(R.string.history_source_preset_named, it) } ?: stringResource(entry.source.label()),
            )
            Fact(stringResource(R.string.detail_environment), entry.environmentName ?: stringResource(R.string.environment_none))
            Fact(stringResource(R.string.detail_total_time), formatDuration(entry.duration))
            response?.let { received ->
                Fact(stringResource(R.string.detail_status), "${received.statusCode} ${received.statusMessage}".trimEnd())
                Fact(stringResource(R.string.detail_protocol), received.protocol)
                received.timings.dns?.let { Fact(stringResource(R.string.detail_dns), formatDuration(it)) }
                received.timings.connect?.let { Fact(stringResource(R.string.detail_connect), formatDuration(it)) }
                received.timings.tls?.let { Fact(stringResource(R.string.detail_tls), formatDuration(it)) }
                received.timings.firstByte?.let { Fact(stringResource(R.string.detail_first_byte), formatDuration(it)) }
                received.body?.let { body -> Fact(stringResource(R.string.detail_size), formatFileSize(context, body.totalBytes)) }
                Fact(stringResource(R.string.detail_final_url), received.finalUrl, code = true)
            }
            (entry.outcome as? Outcome.Failed)?.let { Fact(stringResource(R.string.detail_error), it.error.message()) }
        }
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
        LazyColumn(contentPadding = PaddingValues(DetailPadding), verticalArrangement = Arrangement.spacedBy(DetailSpacing)) {
            items(response.headers) { header -> HeaderLine(header.name, header.value) }
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
            Modifier.verticalScroll(rememberScrollState()).padding(DetailPadding),
            verticalArrangement = Arrangement.spacedBy(DetailSpacing),
        ) {
            Text("${request.method} ${request.url}", style = MaterialTheme.typography.codeMedium, fontWeight = FontWeight.Bold)
            request.headers.forEach { HeaderLine(it.name, it.value) }
            when (val body = request.body) {
                null -> {}

                is HttpBody.Text -> {
                    HeaderLine(CONTENT_TYPE, body.contentType)
                    Text(body.text, style = MaterialTheme.typography.codeSmall, modifier = Modifier.padding(top = DetailSpacing))
                }

                is HttpBody.File -> {
                    HeaderLine(CONTENT_TYPE, body.contentType)
                    Muted(stringResource(R.string.detail_request_file, body.file.name))
                }

                is HttpBody.Multipart -> {
                    HeaderLine(CONTENT_TYPE, body.contentType)
                    body.parts.forEach { part ->
                        when (part) {
                            is HttpPart.Text -> HeaderLine(part.name, part.value)
                            is HttpPart.File -> HeaderLine(part.name, stringResource(R.string.detail_request_file, part.file.name))
                        }
                    }
                }
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
        modifier.verticalScroll(rememberScrollState()).padding(DetailPadding),
        verticalArrangement = Arrangement.spacedBy(DetailSpacing),
    ) {
        OutlinedButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CURL_LABEL, curl))) } }) {
            Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null)
            Text(stringResource(R.string.detail_copy_curl), modifier = Modifier.padding(start = DetailSpacing))
        }
        SelectionContainer { Text(curl, style = MaterialTheme.typography.codeSmall) }
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
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ANY_TYPE)) { uri -> uri?.let { onSave(file, it) } }
    OutlinedButton(onClick = { launcher.launch(suggestedName) }, modifier = modifier) {
        Icon(painterResource(R.drawable.ic_download), contentDescription = null)
        Text(stringResource(R.string.detail_save_body), modifier = Modifier.padding(start = DetailSpacing))
    }
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
private fun Fact(
    label: String,
    value: String,
    code: Boolean = false,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (code) MaterialTheme.typography.codeMedium else MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun HeaderLine(
    name: String,
    value: String,
) {
    Text(
        text =
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(name)
                pop()
                append(": ")
                append(value)
            },
        style = MaterialTheme.typography.codeSmall,
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
private const val CONTENT_TYPE = "Content-Type"
private const val CURL_LABEL = "cURL"
private const val ANY_TYPE = "*/*"
