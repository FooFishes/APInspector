package work.foofish.apinspector.request.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.request.AssemblyProblem
import work.foofish.apinspector.request.RequestPreview
import work.foofish.apinspector.request.SendProblem
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeSmall

/**
 * 请求预览：填参数的同时就能看到实际会发出的 URL（变量、路径参数与查询参数都已代入），点一下即可复制；
 * 当前还发不出去时（缺变量、没有环境等）改为直接列出原因，不必等到点"发送"才知道。
 */
@Composable
internal fun RequestPreviewCard(
    preview: RequestPreview,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val url = preview.url
    if (url != null) {
        UrlPreview(url, modifier)
    } else {
        BlockedNotice(preview.problems, onManageEnvironments, modifier)
    }
}

@Composable
private fun UrlPreview(
    url: String,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Surface(
        onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(URL_LABEL, url))) } },
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.card,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PreviewPaddingHorizontal, vertical = PreviewPaddingVertical),
            horizontalArrangement = Arrangement.spacedBy(PreviewSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = urlText(url),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.codeSmall,
                maxLines = URL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                painterResource(R.drawable.ic_content_copy),
                contentDescription = stringResource(R.string.request_copy_url),
                modifier = Modifier.size(CopyIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 地址的三段各用一种颜色：协议与主机退后，路径最醒目，查询串用主色。 */
@Composable
private fun urlText(url: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    val schemeEnd = url.indexOf(SCHEME_SEPARATOR).let { if (it < 0) 0 else it + SCHEME_SEPARATOR.length }
    val pathStart = url.indexOf('/', startIndex = schemeEnd)
    val origin = if (pathStart < 0) url else url.substring(0, pathStart)
    val rest = if (pathStart < 0) "" else url.substring(pathStart)
    val path = rest.substringBefore('?')
    val query = rest.substring(path.length)
    return buildAnnotatedString {
        withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(origin) }
        withStyle(SpanStyle(color = colors.onSurface)) { append(path) }
        withStyle(SpanStyle(color = colors.primary)) { append(query) }
    }
}

@Composable
private fun BlockedNotice(
    problems: ImmutableList<SendProblem>,
    onManageEnvironments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val environmentRelated =
        problems.any { it is SendProblem.NoEnvironment || (it is SendProblem.Assembly && it.problem !is AssemblyProblem.FileNotSelected) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = PreviewPaddingHorizontal, top = PreviewPaddingVertical, bottom = PreviewPaddingVertical),
            horizontalArrangement = Arrangement.spacedBy(PreviewSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(NoticeIconSize))
            Column(Modifier.weight(1f)) {
                problems.forEach { Text(it.message(), style = MaterialTheme.typography.bodyMedium) }
            }
            if (environmentRelated) {
                TextButton(
                    onClick = onManageEnvironments,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text(stringResource(R.string.request_edit_environment), maxLines = 1) }
            }
        }
    }
}

private const val URL_LABEL = "URL"
private const val SCHEME_SEPARATOR = "://"
private const val URL_MAX_LINES = 4
private val PreviewPaddingHorizontal = 16.dp
private val PreviewPaddingVertical = 12.dp
private val PreviewSpacing = 12.dp
private val CopyIconSize = 18.dp
private val NoticeIconSize = 20.dp
