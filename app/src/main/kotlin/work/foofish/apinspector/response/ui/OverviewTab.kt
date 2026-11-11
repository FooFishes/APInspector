package work.foofish.apinspector.response.ui

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.history.HistoryEntry
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.history.Timings
import work.foofish.apinspector.ui.component.SectionCard
import work.foofish.apinspector.ui.text.formatDateTime
import work.foofish.apinspector.ui.text.formatDuration
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.codeSmall
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 概览（PRD 7.2）：这次请求的来龙去脉。
 *
 * 状态与大小已在顶部的结果摘要里；这里依次是失败原因、耗时分解、请求的背景（何时、从哪发、哪个环境、什么协议）与最终 URL。
 */
@Composable
internal fun OverviewTab(
    entry: HistoryEntry,
    modifier: Modifier = Modifier,
) {
    val response = (entry.outcome as? Outcome.Received)?.response
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(DetailPadding).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OverviewSpacing),
    ) {
        (entry.outcome as? Outcome.Failed)?.let { ErrorCard(it.error.message()) }
        TimingCard(entry.duration, response?.timings)
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                Tile(stringResource(R.string.detail_sent_at), formatDateTime(entry.sentAt))
                Tile(
                    stringResource(R.string.detail_source),
                    entry.presetName?.let { stringResource(R.string.history_source_preset_named, it) }
                        ?: stringResource(entry.source.label()),
                )
            }
            Row(Modifier.padding(top = TileSpacing), horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                Tile(stringResource(R.string.detail_environment), entry.environmentName ?: stringResource(R.string.environment_none))
                Tile(stringResource(R.string.detail_protocol), response?.protocol ?: stringResource(R.string.detail_none))
            }
        }
        response?.let { UrlCard(it.finalUrl) }
    }
}

/** 一项事实：小号的名称在上，值在下；两个一行。 */
@Composable
private fun RowScope.Tile(
    label: String,
    value: String,
) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LabelSpacing)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(DetailPadding),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_error), contentDescription = null)
            SelectionContainer { Text(message, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/**
 * 耗时分解（PRD 7.2）：一根按阶段分段的条，一眼看出时间花在哪；下面逐项列出时长与占比。
 *
 * 各阶段依次发生、互不重叠；总耗时减去它们之和是"其余"（发送请求、下载 Body 等）。复用连接时没有 DNS / 连接 / TLS。
 */
@Composable
private fun TimingCard(
    total: Duration,
    timings: Timings?,
) {
    val colors = MaterialTheme.colorScheme
    val measured =
        listOfNotNull(
            timings?.dns?.let { Phase(R.string.detail_dns, it, colors.tertiary) },
            timings?.connect?.let { Phase(R.string.detail_connect, it, colors.secondary) },
            timings?.tls?.let { Phase(R.string.detail_tls, it, colors.primary) },
            timings?.firstByte?.let { Phase(R.string.detail_first_byte, it, colors.inversePrimary) },
        )
    val rest = total - measured.fold(Duration.ZERO) { sum, phase -> sum + phase.duration }
    val phases =
        if (measured.isEmpty() ||
            rest.isNegative
        ) {
            measured
        } else {
            measured + Phase(R.string.detail_rest, rest, colors.outlineVariant)
        }
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.detail_total_time),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(formatDuration(total), style = MaterialTheme.typography.titleLargeEmphasized)
        }
        if (phases.isEmpty()) return@SectionCard
        val totalMillis = total.toMillis().coerceAtLeast(1).toFloat()
        Row(
            modifier = Modifier.padding(top = BarTopSpacing, bottom = BarBottomSpacing).fillMaxWidth().height(BarHeight),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
        ) {
            phases.forEach { phase ->
                // 极短的阶段也保留一小段，不至于在条上消失。
                val weight = (phase.duration.toMillis() / totalMillis).coerceAtLeast(MIN_SEGMENT_WEIGHT)
                Box(
                    Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(phase.color),
                )
            }
        }
        phases.forEach { phase -> PhaseRow(phase, (phase.duration.toMillis() / totalMillis * PERCENT).roundToInt()) }
    }
}

private class Phase(
    val label: Int,
    val duration: Duration,
    val color: Color,
)

@Composable
private fun PhaseRow(
    phase: Phase,
    percent: Int,
) {
    Row(
        modifier = Modifier.padding(vertical = PhaseRowPadding),
        horizontalArrangement = Arrangement.spacedBy(TileSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(DotSize).clip(CircleShape).background(phase.color))
        Text(stringResource(phase.label), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatDuration(phase.duration), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = stringResource(R.string.detail_percent, percent),
            // 定宽右对齐：占比位数不同，时长那一列仍然对齐。
            modifier = Modifier.width(PercentWidth),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

/** 最终 URL：跟随重定向后的地址，可一键复制。 */
@Composable
private fun UrlCard(url: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_final_url),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(URL_LABEL, url))) } }) {
                Icon(painterResource(R.drawable.ic_content_copy), contentDescription = stringResource(R.string.action_copy))
            }
        }
        SelectionContainer { Text(url, style = MaterialTheme.typography.codeSmall) }
    }
}

private const val URL_LABEL = "URL"
private const val PERCENT = 100
private const val MIN_SEGMENT_WEIGHT = 0.015f
private val OverviewSpacing = 12.dp
private val TileSpacing = 12.dp
private val LabelSpacing = 2.dp
private val BarHeight = 12.dp
private val BarGap = 2.dp
private val BarTopSpacing = 12.dp
private val BarBottomSpacing = 8.dp
private val PhaseRowPadding = 4.dp
private val DotSize = 10.dp
private val PercentWidth = 44.dp
