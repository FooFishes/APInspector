package work.foofish.apinspector.response.ui

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import work.foofish.apinspector.R
import work.foofish.apinspector.history.Outcome
import work.foofish.apinspector.response.BodyDisplay
import work.foofish.apinspector.response.BodyView
import work.foofish.apinspector.response.JsonViewKind
import work.foofish.apinspector.response.JsonViewRow
import work.foofish.apinspector.response.ResponseDetail
import work.foofish.apinspector.ui.component.ChoiceGroup
import work.foofish.apinspector.ui.component.PillSearchField
import work.foofish.apinspector.ui.component.rememberSyncedTextFieldState
import work.foofish.apinspector.ui.text.formatFileSize
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeSmall
import java.io.File

/** Body：JSON 折叠树 ⇄ 格式化原文、搜索、长按复制；图片预览；二进制可另存（PRD 7.2）。 */
@Composable
internal fun BodyTab(
    detail: ResponseDetail,
    actions: ResponseDetailActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val response = (detail.entry.outcome as? Outcome.Received)?.response
    val suggestedName =
        remember(
            detail.entry.request.url,
        ) {
            detail.entry.request.url
                .substringBefore('?')
                .substringAfterLast('/')
                .ifEmpty { DEFAULT_FILE_NAME }
        }
    Column(modifier) {
        response?.body?.takeIf { it.truncated }?.let { stored ->
            Notice(
                stringResource(
                    R.string.detail_truncated,
                    formatFileSize(context, stored.storedBytes),
                    formatFileSize(context, stored.totalBytes),
                ),
            )
        }
        when (val body = detail.body) {
            BodyView.Empty -> {
                CenteredMessage(stringResource(R.string.detail_no_body), Modifier.fillMaxWidth())
            }

            BodyView.Cleared -> {
                CenteredMessage(stringResource(R.string.detail_body_cleared), Modifier.fillMaxWidth())
            }

            is BodyView.Json -> {
                TextualBody(detail, body.element != null, body.limited, body.file, suggestedName, actions, Modifier.weight(1f))
            }

            is BodyView.Text -> {
                TextualBody(
                    detail,
                    treeAvailable = false,
                    limited = body.limited,
                    file = body.file,
                    suggestedName = suggestedName,
                    actions = actions,
                    modifier = Modifier.weight(1f),
                )
            }

            is BodyView.Image -> {
                ImageBody(body.file, suggestedName, actions)
            }

            is BodyView.Binary -> {
                Column(Modifier.padding(DetailPadding), verticalArrangement = Arrangement.spacedBy(DetailSpacing)) {
                    Muted(stringResource(R.string.detail_binary, response?.contentType ?: stringResource(R.string.detail_unknown_type)))
                    body.file?.let { SaveBodyButton(it, suggestedName, actions.onSaveBody) }
                        ?: Muted(stringResource(R.string.detail_cannot_save_truncated))
                }
            }
        }
    }
}

@Composable
private fun TextualBody(
    detail: ResponseDetail,
    treeAvailable: Boolean,
    limited: Boolean,
    file: File?,
    suggestedName: String,
    actions: ResponseDetailActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DetailSpacing)) {
        // 工具行只占一行：平时是显示方式、搜索与另存；点搜索后整行变成搜索框。
        val query = rememberSyncedTextFieldState(detail.query, actions.onQueryChange)
        var searching by rememberSaveable { mutableStateOf(detail.query.isNotEmpty()) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = DetailPadding, end = DetailPadding, top = DetailSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (searching) {
                PillSearchField(query, stringResource(R.string.detail_search), Modifier.weight(1f), onClose = { searching = false })
            } else {
                if (treeAvailable) DisplaySwitch(detail.display, actions.onDisplayChange)
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = { searching = true }) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.detail_search))
                }
                file?.let { SaveBodyIconButton(it, suggestedName, actions.onSaveBody) }
            }
        }
        if (limited) Notice(stringResource(R.string.detail_display_limited))
        val rows = detail.rows
        Surface(
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).fillMaxWidth()
                    .padding(horizontal = DetailPadding)
                    .padding(bottom = DetailPadding)
                    .navigationBarsPadding(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.card,
        ) {
            if (rows != null) {
                LazyColumn(contentPadding = PaddingValues(vertical = DetailSpacing)) {
                    items(rows, key = JsonViewRow::path) { row -> JsonTreeRow(row, actions.onToggle) }
                }
            } else {
                RawText(detail.formatted.orEmpty(), detail.query)
            }
        }
    }
}

@Composable
private fun DisplaySwitch(
    display: BodyDisplay,
    onChange: (BodyDisplay) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChoiceGroup(
        options = BodyDisplays,
        selected = display,
        onSelect = onChange,
        label = { stringResource(if (it == BodyDisplay.TREE) R.string.detail_tree else R.string.detail_raw) },
        modifier = modifier,
    )
}

/** 折叠树的一行；长按复制值或路径。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonTreeRow(
    row: JsonViewRow,
    onToggle: (String) -> Unit,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val container = row.kind == JsonViewKind.OBJECT || row.kind == JsonViewKind.ARRAY
    val rotation by animateFloatAsState(if (row.expanded) 0f else COLLAPSED_ROTATION, label = "chevron")
    Box {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { if (container) onToggle(row.path) }, onLongClick = { menu = true })
                    .padding(
                        start = DetailPadding + TreeIndent * row.depth,
                        end = DetailPadding,
                        top = TreeRowPadding,
                        bottom = TreeRowPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (container) {
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(TreeIconSize).rotate(rotation),
                )
            }
            Text(text = treeText(row), style = MaterialTheme.typography.codeSmall)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
            row.value?.let { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_copy_value)) },
                    onClick = {
                        menu = false
                        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(row.path, value))) }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.detail_copy_path)) },
                onClick = {
                    menu = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(row.path, row.path))) }
                },
            )
        }
    }
}

@Composable
private fun treeText(row: JsonViewRow): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return buildAnnotatedString {
        row.key?.let { key ->
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(key) }
            append(": ")
        }
        when (row.kind) {
            JsonViewKind.OBJECT -> withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("{${row.size}}") }
            JsonViewKind.ARRAY -> withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append("[${row.size}]") }
            JsonViewKind.STRING -> withStyle(SpanStyle(color = colors.tertiary)) { append("\"${row.value}\"") }
            JsonViewKind.NUMBER, JsonViewKind.BOOLEAN -> withStyle(SpanStyle(color = colors.primary)) { append(row.value.orEmpty()) }
            JsonViewKind.NULL -> withStyle(SpanStyle(color = colors.onSurfaceVariant)) { append(row.value.orEmpty()) }
        }
    }
}

/** 原文按行显示，[query] 的出现处高亮；可选择复制。 */
@Composable
private fun RawText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
) {
    val lines = remember(text) { text.lines() }
    val highlight =
        SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
    val needle = query.trim()
    SelectionContainer(modifier) {
        LazyColumn(modifier = Modifier.horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(DetailPadding)) {
            items(lines) { line ->
                Text(
                    text = if (needle.isEmpty()) AnnotatedString(line) else highlighted(line, needle, highlight),
                    style = MaterialTheme.typography.codeSmall,
                    softWrap = false,
                )
            }
        }
    }
}

private fun highlighted(
    line: String,
    needle: String,
    style: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        var start = 0
        while (true) {
            val index = line.indexOf(needle, start, ignoreCase = true)
            if (index < 0) break
            append(line.substring(start, index))
            withStyle(style) { append(line.substring(index, index + needle.length)) }
            start = index + needle.length
        }
        append(line.substring(start))
    }

/** 图片预览：按显示需要缩小解码，避免大图占用过多内存。 */
@Composable
private fun ImageBody(
    file: File,
    suggestedName: String,
    actions: ResponseDetailActions,
) {
    val bitmap by produceState<Bitmap?>(null, file) { value = withContext(Dispatchers.IO) { decodeSampled(file) } }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(DetailPadding),
        verticalArrangement = Arrangement.spacedBy(DetailSpacing),
    ) {
        SaveBodyButton(file, suggestedName, actions.onSaveBody)
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth()) }
            ?: Muted(stringResource(R.string.detail_image_unreadable))
    }
}

private fun decodeSampled(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_DIMENSION) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = DetailPadding, vertical = DetailSpacing),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private const val DEFAULT_FILE_NAME = "response"
private const val COLLAPSED_ROTATION = -90f
private const val MAX_IMAGE_DIMENSION = 2048
private val BodyDisplays = BodyDisplay.entries.toImmutableList()
private val TreeIndent = 16.dp
private val TreeRowPadding = 4.dp
private val TreeIconSize = 18.dp
