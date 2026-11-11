package work.foofish.apinspector.operationdoc

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.response.ui.statusColors
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.GroupPosition
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.SectionLabel
import work.foofish.apinspector.ui.component.groupedCard
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.cardInset
import work.foofish.apinspector.ui.theme.codeLabel
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall

/** "文档" Tab（PRD 3）。 */
@Composable
fun OperationDocTab(
    viewModel: OperationDocViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        OperationDocUiState.Loading -> {
            LoadingState(modifier)
        }

        OperationDocUiState.Missing -> {
            Centered(modifier) {
                EmptyState(painterResource(R.drawable.ic_error), stringResource(R.string.operations_spec_missing), body = null)
            }
        }

        is OperationDocUiState.Failed -> {
            Centered(modifier) {
                EmptyState(
                    painterResource(R.drawable.ic_error),
                    stringResource(R.string.operations_load_failed_title),
                    current.failure.message(),
                )
            }
        }

        is OperationDocUiState.Loaded -> {
            DocList(
                overview = current.overview,
                items = current.items,
                onToggle = viewModel::toggle,
                onSelectContentType = viewModel::selectContentType,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun DocList(
    overview: OperationOverview,
    items: ImmutableList<DocItem>,
    onToggle: (String) -> Unit,
    onSelectContentType: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placements = remember(items) { items.placements() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = ScreenPadding, bottom = BottomPadding),
    ) {
        item(key = OVERVIEW_KEY) { Overview(overview, Modifier.padding(horizontal = ScreenPadding)) }
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val placement = placements[index]
            if (placement == null) {
                DocItemRow(item, onToggle, onSelectContentType, Modifier.animateItem())
            } else {
                DocItemRow(
                    item = item,
                    onToggle = onToggle,
                    onSelectContentType = onSelectContentType,
                    modifier =
                        Modifier
                            .animateItem()
                            .padding(top = if (placement.gapAbove) CardGap else NoGap)
                            .groupedCard(placement.position)
                            .padding(
                                top = if (placement.position.startsCard) CardEdgePadding else NoGap,
                                bottom = if (placement.position.endsCard) CardEdgePadding else NoGap,
                            ),
                )
            }
        }
    }
}

/**
 * 一行在卡片中的位置。
 *
 * @property gapAbove 这一行开始了同一分区中的又一张卡片，与上一张之间留出间隙。
 */
private class Placement(
    val position: GroupPosition,
    val gapAbove: Boolean,
)

private val GroupPosition.startsCard: Boolean get() = this == GroupPosition.SINGLE || this == GroupPosition.TOP
private val GroupPosition.endsCard: Boolean get() = this == GroupPosition.SINGLE || this == GroupPosition.BOTTOM

/**
 * 把文档的各行归入卡片：分区标题在卡片之外（对应 `null`）；每个参数位置、每个响应各成一张卡片，
 * 其余内容（请求体的 schema 等）与所在分区共用一张。
 */
private fun List<DocItem>.placements(): List<Placement?> =
    mapIndexed { index, item ->
        if (item is DocItem.SectionTitle) return@mapIndexed null
        val first = startsCard(index)
        val last = index == lastIndex || !continuesCard(index + 1)
        Placement(cardPosition(first, last), gapAbove = first && !followsTitle(index))
    }

private fun List<DocItem>.followsTitle(index: Int): Boolean = index == 0 || this[index - 1] is DocItem.SectionTitle

/** 第 [index] 行是否开始一张新卡片。 */
private fun List<DocItem>.startsCard(index: Int): Boolean =
    followsTitle(index) || this[index] is DocItem.ParameterGroupTitle || this[index] is DocItem.ResponseRow

/** 第 [index] 行是否仍属于上一行所在的卡片。 */
private fun List<DocItem>.continuesCard(index: Int): Boolean = this[index] !is DocItem.SectionTitle && !startsCard(index)

private fun cardPosition(
    first: Boolean,
    last: Boolean,
): GroupPosition =
    when {
        first && last -> GroupPosition.SINGLE
        first -> GroupPosition.TOP
        last -> GroupPosition.BOTTOM
        else -> GroupPosition.MIDDLE
    }

@Composable
private fun DocItemRow(
    item: DocItem,
    onToggle: (String) -> Unit,
    onSelectContentType: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is DocItem.SectionTitle -> {
            SectionTitle(item.section, modifier)
        }

        is DocItem.Note -> {
            Indented(item.depth, modifier, gutter = item.depth > 0) { Muted(stringResource(item.note.label())) }
        }

        is DocItem.Description -> {
            Indented(item.depth, modifier, gutter = item.depth > 0) {
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is DocItem.ParameterGroupTitle -> {
            ParameterGroupTitle(item.location, modifier)
        }

        is DocItem.ParameterRow -> {
            ParameterRow(item, onToggle = { onToggle(item.id) }, modifier)
        }

        is DocItem.SchemaRow -> {
            SchemaRow(item, onToggle = { onToggle(item.id) }, modifier)
        }

        is DocItem.MediaTypes -> {
            MediaTypes(item, onSelectContentType, modifier)
        }

        is DocItem.ExampleBlock -> {
            ExampleBlock(item, modifier)
        }

        is DocItem.ResponseRow -> {
            ResponseRow(item, onToggle = { onToggle(item.id) }, modifier)
        }

        is DocItem.HeaderRow -> {
            HeaderRow(item, modifier)
        }

        is DocItem.UnresolvedRow -> {
            UnresolvedRow(item, modifier)
        }
    }
}

@Composable
private fun ParameterGroupTitle(
    location: ParameterLocation,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(location.label()),
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = RowVerticalPadding),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ParameterRow(
    item: DocItem.ParameterRow,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TreeRow(depth = 0, expandable = item.expandable, expanded = item.expanded, onToggle = onToggle, modifier = modifier) {
        NameLine(name = item.name, type = item.type, required = item.required, deprecated = item.deprecated) {
            if (item.ignored) Flag(stringResource(R.string.doc_ignored_header))
        }
        item.description?.let { Muted(it) }
        Facts(item.facts)
    }
}

@Composable
private fun SchemaRow(
    item: DocItem.SchemaRow,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TreeRow(depth = item.depth, expandable = item.expandable, expanded = item.expanded, onToggle = onToggle, modifier = modifier) {
        NameLine(name = item.label?.text(), type = item.type, required = item.required, deprecated = item.flags.deprecated) {
            if (item.flags.readOnly) Flag(stringResource(R.string.doc_read_only))
            if (item.flags.writeOnly) Flag(stringResource(R.string.doc_write_only))
        }
        item.description?.let { Muted(it) }
        Facts(item.facts)
    }
}

/** 一个响应：状态码胶囊按类别着色，一眼分辨成功与各类错误；其后是摘要与描述。 */
@Composable
private fun ResponseRow(
    item: DocItem.ResponseRow,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.summary ?: item.description
    val detail = item.description.takeIf { item.summary != null }
    TreeRow(depth = 0, expandable = item.expandable, expanded = item.expanded, onToggle = onToggle, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing)) {
            StatusPill(item.status)
            Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) {
                title?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                detail?.let { Muted(it) }
            }
        }
    }
}

/** 声明的状态码：具体状态码与 `2XX` 这样的范围按类别着色，`default` 等用中性色。 */
@Composable
private fun StatusPill(
    status: String,
    modifier: Modifier = Modifier,
) {
    val code = status.toIntOrNull() ?: status.firstOrNull()?.digitToIntOrNull()?.times(STATUS_CLASS) ?: UNKNOWN_STATUS
    val (container, content) = statusColors(code)
    Surface(modifier = modifier, shape = CircleShape, color = container, contentColor = content) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = PillPaddingHorizontal, vertical = PillPaddingVertical),
            style = MaterialTheme.typography.codeLabel,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HeaderRow(
    item: DocItem.HeaderRow,
    modifier: Modifier = Modifier,
) {
    Indented(item.depth, modifier, verticalPadding = RowVerticalPadding) {
        NameLine(name = item.name, type = item.type, required = item.required, deprecated = false) {}
        item.description?.let { Muted(it) }
    }
}

@Composable
private fun UnresolvedRow(
    item: DocItem.UnresolvedRow,
    modifier: Modifier = Modifier,
) {
    Indented(item.depth, modifier, verticalPadding = RowVerticalPadding) {
        Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(SmallIcon),
            )
            Text(
                text = stringResource(R.string.doc_unresolved, item.ref),
                style = MaterialTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Muted(item.reason.label())
    }
}

@Composable
private fun SectionTitle(
    section: DocSection,
    modifier: Modifier = Modifier,
) {
    SectionLabel(
        text =
            stringResource(
                when (section) {
                    DocSection.PARAMETERS -> R.string.doc_parameters
                    DocSection.REQUEST_BODY -> R.string.doc_request_body
                    DocSection.RESPONSES -> R.string.doc_responses
                },
            ),
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
    )
}

/**
 * 可展开的树形行：左侧为展开箭头（不可展开时留空对齐），整行可点按；嵌套层级以竖直引导线标出。
 */
@Composable
private fun TreeRow(
    depth: Int,
    expandable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 0f else COLLAPSED_ROTATION, label = "tree-chevron")
    val toggleLabel = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.clickable(onClickLabel = toggleLabel, onClick = onToggle) else Modifier)
                .indentGuides(depth, MaterialTheme.colorScheme.outlineVariant)
                .padding(
                    start = ScreenPadding + IndentStep * depth,
                    end = ScreenPadding,
                    top = RowVerticalPadding,
                    bottom = RowVerticalPadding,
                ),
        horizontalArrangement = Arrangement.spacedBy(InlineSpacing),
    ) {
        if (expandable) {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                modifier = Modifier.size(ChevronSize).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.size(ChevronSize))
        }
        Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) { content() }
    }
}

/** 为第 1 到 [depth] 层各画一条竖直引导线，位置在该层父节点的展开箭头正下方；相邻行的线首尾相接。 */
private fun Modifier.indentGuides(
    depth: Int,
    color: Color,
): Modifier =
    if (depth <= 0) {
        this
    } else {
        drawBehind {
            val stroke = GuideWidth.toPx()
            repeat(depth) { level ->
                val x = (ScreenPadding + IndentStep * level + ChevronSize / 2).toPx()
                drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            }
        }
    }

/**
 * 名称行：等宽加粗的名称（必填时带错误色的 `*`）、类型，以及已弃用等标记。
 *
 * @param flags 追加在行尾的标记。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameLine(
    name: String?,
    type: TypeLabel?,
    required: Boolean,
    deprecated: Boolean,
    modifier: Modifier = Modifier,
    flags: @Composable FlowRowScope.() -> Unit,
) {
    val requiredColor = MaterialTheme.colorScheme.error
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InlineSpacing),
        verticalArrangement = Arrangement.spacedBy(LineSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        name?.let {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(SpanStyle(textDecoration = if (deprecated) TextDecoration.LineThrough else null)) { append(it) }
                        if (required) withStyle(SpanStyle(color = requiredColor)) { append(REQUIRED_MARK) }
                    },
                style = MaterialTheme.typography.codeMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        type?.let { TypeText(it) }
        if (deprecated) Flag(stringResource(R.string.doc_deprecated), warning = true)
        flags()
    }
}

@Composable
private fun TypeText(type: TypeLabel) {
    when (type.kind) {
        TypeLabel.Kind.PLAIN -> {
            Text(type.text, style = MaterialTheme.typography.codeSmall, color = MaterialTheme.colorScheme.tertiary)
        }

        TypeLabel.Kind.REFERENCE -> {
            Text(
                type.text,
                style = MaterialTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        TypeLabel.Kind.UNRESOLVED -> {
            Text(
                stringResource(R.string.doc_unresolved, type.text),
                style = MaterialTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 名称行里的小标记（只读、已弃用等）；[warning] 时用错误色。 */
@Composable
private fun Flag(
    text: String,
    modifier: Modifier = Modifier,
    warning: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (warning) colors.errorContainer else colors.cardInset,
        contentColor = if (warning) colors.onErrorContainer else colors.onSurfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = FlagPaddingHorizontal, vertical = FlagPaddingVertical),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 约束与附加信息：一排小块而不是一句话，可选值逐个列出。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(
    facts: ImmutableList<SchemaFact>,
    modifier: Modifier = Modifier,
) {
    if (facts.isEmpty()) return
    FlowRow(
        modifier = modifier.padding(top = FactsTopSpacing),
        horizontalArrangement = Arrangement.spacedBy(FactSpacing),
        verticalArrangement = Arrangement.spacedBy(FactSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        facts.forEach { FactChips(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.FactChips(fact: SchemaFact) {
    when (fact) {
        is SchemaFact.Enum -> {
            Text(
                text = stringResource(R.string.doc_fact_enum),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fact.values.forEach { FactChip(label = null, value = it) }
        }

        is SchemaFact.Default -> {
            FactChip(stringResource(R.string.doc_fact_default), fact.value)
        }

        is SchemaFact.Example -> {
            FactChip(stringResource(R.string.doc_example), fact.value)
        }

        is SchemaFact.Pattern -> {
            FactChip(stringResource(R.string.doc_fact_pattern), fact.regex)
        }

        else -> {
            fact.constraintText()?.let { FactChip(label = it, value = null) }
        }
    }
}

/** 一块约束：[label] 为说明（弱化），[value] 为取值（等宽）。 */
@Composable
private fun FactChip(
    label: String?,
    value: String?,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.cardInset) {
        Row(
            modifier = Modifier.padding(horizontal = FactPaddingHorizontal, vertical = FactPaddingVertical),
            horizontalArrangement = Arrangement.spacedBy(FactLabelSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            value?.let {
                Text(it, style = MaterialTheme.typography.codeSmall, maxLines = FACT_VALUE_MAX_LINES, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 范围、长度、个数这类自成一句的约束；带取值的约束（可选值、默认、示例、格式）返回 `null`。 */
@Composable
private fun SchemaFact.constraintText(): String? =
    when (this) {
        is SchemaFact.Minimum -> stringResource(if (exclusive) R.string.doc_fact_minimum_exclusive else R.string.doc_fact_minimum, value)
        is SchemaFact.Maximum -> stringResource(if (exclusive) R.string.doc_fact_maximum_exclusive else R.string.doc_fact_maximum, value)
        is SchemaFact.MinLength -> stringResource(R.string.doc_fact_min_length, value)
        is SchemaFact.MaxLength -> stringResource(R.string.doc_fact_max_length, value)
        is SchemaFact.MinItems -> stringResource(R.string.doc_fact_min_items, value)
        is SchemaFact.MaxItems -> stringResource(R.string.doc_fact_max_items, value)
        SchemaFact.UniqueItems -> stringResource(R.string.doc_fact_unique_items)
        is SchemaFact.Enum, is SchemaFact.Default, is SchemaFact.Example, is SchemaFact.Pattern -> null
    }

@Composable
private fun NodeLabel.text(): String =
    when (this) {
        is NodeLabel.Property -> name
        is NodeLabel.Composition -> "$keyword #$index"
        NodeLabel.AdditionalProperties -> stringResource(R.string.doc_additional_properties)
        NodeLabel.Not -> NOT_KEYWORD
        NodeLabel.ItemSchema -> stringResource(R.string.doc_item_schema)
    }

/** content type 选择：单行横向滚动，左右留白随内容一起滚动，滚到头时贴着卡片边缘。 */
@Composable
private fun MediaTypes(
    item: DocItem.MediaTypes,
    onSelect: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .indentGuides(item.depth, colors.outlineVariant)
                .horizontalScroll(rememberScrollState())
                .padding(start = contentStart(item.depth, gutter = item.depth > 0), end = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(InlineSpacing),
    ) {
        item.contentTypes.forEach { contentType ->
            FilterChip(
                selected = contentType == item.selected,
                onClick = { onSelect(item.ownerId, contentType) },
                label = { Text(contentType, style = MaterialTheme.typography.codeSmall) },
                shape = CircleShape,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = colors.cardInset,
                        selectedContainerColor = colors.secondaryContainer,
                        selectedLabelColor = colors.onSecondaryContainer,
                    ),
                border = null,
            )
        }
    }
}

/** 示例：标题行右侧可一键复制，代码块嵌在卡片里、可横向滚动与选中。 */
@Composable
private fun ExampleBlock(
    item: DocItem.ExampleBlock,
    modifier: Modifier = Modifier,
) {
    val copy = rememberCopy()
    Indented(item.depth, modifier, gutter = item.depth > 0, verticalPadding = RowVerticalPadding) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.title.text(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { copy(item.code) }, modifier = Modifier.size(CopyButtonSize)) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_copy),
                    contentDescription = stringResource(R.string.doc_copy_example),
                    modifier = Modifier.size(SmallIcon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.cardInset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = item.code,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(CodePadding),
                    style = MaterialTheme.typography.codeSmall,
                )
            }
        }
    }
}

@Composable
private fun ExampleTitle.text(): String =
    when (this) {
        ExampleTitle.Single -> stringResource(R.string.doc_example)
        is ExampleTitle.Named -> stringResource(R.string.doc_example_named, summary ?: name)
        is ExampleTitle.Serialized -> stringResource(R.string.doc_example_serialized, name)
        is ExampleTitle.External -> stringResource(R.string.doc_example_external, name)
    }

/** 第 [depth] 层内容的左边距；[gutter] 时再让出展开箭头的位置，与可折叠行的文字对齐。 */
private fun contentStart(
    depth: Int,
    gutter: Boolean,
): Dp = ScreenPadding + IndentStep * depth + if (gutter) ChevronSize + InlineSpacing else NoGap

/**
 * 按深度缩进的一行，嵌套层级画出引导线。
 *
 * @param gutter 是否在左侧留出折叠箭头的位置，与可折叠的行对齐；分区顶层的说明文字不留，直接贴卡片内边距。
 * @param verticalPadding 行内上下留白；放在引导线之内，线才能与相邻行相接。
 */
@Composable
private fun Indented(
    depth: Int,
    modifier: Modifier = Modifier,
    gutter: Boolean = true,
    verticalPadding: Dp = NoGap,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .indentGuides(depth, MaterialTheme.colorScheme.outlineVariant)
                .padding(start = contentStart(depth, gutter), end = ScreenPadding, top = verticalPadding, bottom = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(LineSpacing),
    ) { content() }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Centered(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@StringRes
private fun DocNote.label(): Int =
    when (this) {
        DocNote.NO_PARAMETERS -> R.string.doc_no_parameters
        DocNote.NO_REQUEST_BODY -> R.string.doc_no_request_body
        DocNote.NO_RESPONSES -> R.string.doc_no_responses
        DocNote.BODY_REQUIRED -> R.string.doc_body_required
        DocNote.RESPONSE_HEADERS -> R.string.doc_response_headers
    }

private const val OVERVIEW_KEY = "overview"
private const val NOT_KEYWORD = "not"
private const val REQUIRED_MARK = "*"
private const val COLLAPSED_ROTATION = -90f
private const val FACT_VALUE_MAX_LINES = 2

/** 状态码每一类的跨度；`2XX` 这样的范围按首位数字归类。 */
private const val STATUS_CLASS = 100
private const val UNKNOWN_STATUS = 0
private val IndentStep = 16.dp
private val GuideWidth = 1.dp
private val BottomPadding = 32.dp
private val CardGap = 8.dp
private val CardEdgePadding = 8.dp
private val NoGap = 0.dp
private val PillPaddingHorizontal = 8.dp
private val PillPaddingVertical = 2.dp
private val FlagPaddingHorizontal = 6.dp
private val FlagPaddingVertical = 1.dp
private val FactsTopSpacing = 2.dp
private val FactSpacing = 6.dp
private val FactPaddingHorizontal = 6.dp
private val FactPaddingVertical = 2.dp
private val FactLabelSpacing = 4.dp
private val InlineSpacing = 8.dp
private val LineSpacing = 4.dp
private val RowVerticalPadding = 8.dp
private val ChevronSize = 20.dp
private val SmallIcon = 16.dp
private val CopyButtonSize = 32.dp
private val CodePadding = 12.dp
