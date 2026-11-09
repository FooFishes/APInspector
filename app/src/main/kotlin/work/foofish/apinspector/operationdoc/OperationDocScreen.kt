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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.ParameterLocation
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.text.label
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.codeSmall

/**
 * 接口文档（PRD 3）。
 *
 * @param showBack 单栏布局下显示返回按钮；双栏布局下文档位于详情栏，不需要返回。
 */
@Composable
fun OperationDocScreen(
    viewModel: OperationDocViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val key = (state as? OperationDocUiState.Loaded)?.overview?.key
                    if (key !=
                        null
                    ) {
                        Text(key.path, style = MaterialTheme.typography.codeMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            OperationDocUiState.Loading -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            OperationDocUiState.Missing -> {
                Centered(Modifier.padding(innerPadding)) {
                    EmptyState(painterResource(R.drawable.ic_error), stringResource(R.string.operations_spec_missing), body = null)
                }
            }

            is OperationDocUiState.Failed -> {
                Centered(Modifier.padding(innerPadding)) {
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
                    contentPadding = innerPadding,
                )
            }
        }
    }
}

@Composable
private fun DocList(
    overview: OperationOverview,
    items: ImmutableList<DocItem>,
    onToggle: (String) -> Unit,
    onSelectContentType: (String, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = ScreenPadding),
    ) {
        item(key = OVERVIEW_KEY) { Overview(overview, Modifier.padding(horizontal = ScreenPadding)) }
        items(items, key = DocItem::id) { item ->
            DocItemRow(item, onToggle, onSelectContentType, Modifier.animateItem())
        }
    }
}

@Composable
private fun DocItemRow(
    item: DocItem,
    onToggle: (String) -> Unit,
    onSelectContentType: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is DocItem.SectionTitle -> SectionTitle(item.section, modifier)
        is DocItem.Note -> Indented(item.depth, modifier) { Muted(stringResource(item.note.label())) }
        is DocItem.Description -> Indented(item.depth, modifier) { Text(item.text, style = MaterialTheme.typography.bodyMedium) }
        is DocItem.ParameterGroupTitle -> ParameterGroupTitle(item.location, modifier)
        is DocItem.ParameterRow -> ParameterRow(item, onToggle = { onToggle(item.id) }, modifier)
        is DocItem.SchemaRow -> SchemaRow(item, onToggle = { onToggle(item.id) }, modifier)
        is DocItem.MediaTypes -> MediaTypes(item, onSelectContentType, modifier)
        is DocItem.ExampleBlock -> ExampleBlock(item, modifier)
        is DocItem.ResponseRow -> ResponseRow(item, onToggle = { onToggle(item.id) }, modifier)
        is DocItem.HeaderRow -> HeaderRow(item, modifier)
        is DocItem.UnresolvedRow -> UnresolvedRow(item, modifier)
    }
}

@Composable
private fun ParameterGroupTitle(
    location: ParameterLocation,
    modifier: Modifier = Modifier,
) {
    Indented(depth = 0, modifier = modifier.padding(top = GroupSpacing)) {
        Text(
            text = stringResource(location.label()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParameterRow(
    item: DocItem.ParameterRow,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TreeRow(depth = 0, expandable = item.expandable, expanded = item.expanded, onToggle = onToggle, modifier = modifier) {
        NameLine(name = item.name, type = item.type, required = item.required, deprecated = item.deprecated)
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
        NameLine(name = item.label?.text(), type = item.type, required = item.required, deprecated = item.flags.deprecated)
        SchemaFlagsLine(item.flags)
        item.description?.let { Muted(it) }
        Facts(item.facts)
    }
}

@Composable
private fun ResponseRow(
    item: DocItem.ResponseRow,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TreeRow(depth = 0, expandable = item.expandable, expanded = item.expanded, onToggle = onToggle, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.status,
                style = MaterialTheme.typography.codeMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor(item.status),
            )
            item.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        item.description?.let { Muted(it) }
    }
}

@Composable
private fun HeaderRow(
    item: DocItem.HeaderRow,
    modifier: Modifier = Modifier,
) {
    Indented(item.depth, modifier.padding(vertical = RowVerticalPadding)) {
        NameLine(name = item.name, type = item.type, required = item.required, deprecated = false)
        item.description?.let { Muted(it) }
    }
}

@Composable
private fun UnresolvedRow(
    item: DocItem.UnresolvedRow,
    modifier: Modifier = Modifier,
) {
    Indented(item.depth, modifier.padding(vertical = RowVerticalPadding)) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(
    overview: OperationOverview,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = GroupSpacing), verticalArrangement = Arrangement.spacedBy(InlineSpacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing), verticalAlignment = Alignment.CenterVertically) {
            MethodBadge(overview.key.method)
            SelectionContainer {
                Text(
                    text = overview.key.path,
                    style = MaterialTheme.typography.codeMedium,
                    textDecoration = if (overview.deprecated) TextDecoration.LineThrough else null,
                )
            }
        }
        overview.summary?.let { Text(it, style = MaterialTheme.typography.headlineSmallEmphasized) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(InlineSpacing), verticalArrangement = Arrangement.spacedBy(InlineSpacing)) {
            if (overview.deprecated) {
                Pill(
                    stringResource(R.string.doc_deprecated),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            overview.tags.forEach { Pill(it, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
        }
        overview.operationId?.let {
            Text(
                it,
                style = MaterialTheme.typography.codeSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(InlineSpacing), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(SmallIcon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Muted(overview.securityText())
        }
        overview.description?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodyMedium) } }
    }
}

@Composable
private fun OperationOverview.securityText(): String {
    if (security.isEmpty()) return stringResource(R.string.doc_security_none)
    val anonymous = stringResource(R.string.doc_security_anonymous)
    val combined = stringResource(R.string.doc_security_combined_separator)
    val alternatives =
        security.joinToString(
            stringResource(R.string.doc_security_alternative_separator),
        ) { schemes ->
            schemes.joinToString(combined).ifEmpty {
                anonymous
            }
        }
    return stringResource(R.string.doc_security, alternatives)
}

@Composable
private fun SectionTitle(
    section: DocSection,
    modifier: Modifier = Modifier,
) {
    Text(
        text =
            stringResource(
                when (section) {
                    DocSection.PARAMETERS -> R.string.doc_parameters
                    DocSection.REQUEST_BODY -> R.string.doc_request_body
                    DocSection.RESPONSES -> R.string.doc_responses
                },
            ),
        modifier =
            modifier.fillMaxWidth().padding(
                start = ScreenPadding,
                end = ScreenPadding,
                top = SectionSpacing,
                bottom = InlineSpacing,
            ),
        style = MaterialTheme.typography.titleLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** 可展开的树形行：左侧为展开箭头（不可展开时留空对齐）。 */
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (expandable) Modifier.clickable(onClick = onToggle) else Modifier)
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
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                modifier = Modifier.size(ChevronSize).rotate(rotation),
            )
        } else {
            Spacer(Modifier.size(ChevronSize))
        }
        Column(verticalArrangement = Arrangement.spacedBy(LineSpacing)) { content() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameLine(
    name: String?,
    type: TypeLabel?,
    required: Boolean,
    deprecated: Boolean,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InlineSpacing),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        name?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.codeMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (deprecated) TextDecoration.LineThrough else null,
            )
        }
        type?.let { TypeText(it) }
        if (required) {
            Text(
                stringResource(R.string.doc_required),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (deprecated) {
            Text(
                stringResource(R.string.doc_deprecated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TypeText(type: TypeLabel) {
    when (type.kind) {
        TypeLabel.Kind.PLAIN -> {
            Text(type.text, style = MaterialTheme.typography.codeSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun SchemaFlagsLine(flags: SchemaFlags) {
    val labels =
        listOfNotNull(
            R.string.doc_read_only.takeIf { flags.readOnly },
            R.string.doc_write_only.takeIf { flags.writeOnly },
        )
    if (labels.isNotEmpty()) Muted(labels.map { stringResource(it) }.joinToString(stringResource(R.string.spec_info_separator)))
}

@Composable
private fun Facts(facts: ImmutableList<SchemaFact>) {
    if (facts.isEmpty()) return
    Text(
        text = facts.map { it.text() }.joinToString(stringResource(R.string.spec_info_separator)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SchemaFact.text(): String =
    when (this) {
        is SchemaFact.Enum -> stringResource(R.string.doc_fact_enum, values.joinToString(ENUM_SEPARATOR))
        is SchemaFact.Default -> stringResource(R.string.doc_fact_default, value)
        is SchemaFact.Minimum -> stringResource(if (exclusive) R.string.doc_fact_minimum_exclusive else R.string.doc_fact_minimum, value)
        is SchemaFact.Maximum -> stringResource(if (exclusive) R.string.doc_fact_maximum_exclusive else R.string.doc_fact_maximum, value)
        is SchemaFact.MinLength -> stringResource(R.string.doc_fact_min_length, value)
        is SchemaFact.MaxLength -> stringResource(R.string.doc_fact_max_length, value)
        is SchemaFact.MinItems -> stringResource(R.string.doc_fact_min_items, value)
        is SchemaFact.MaxItems -> stringResource(R.string.doc_fact_max_items, value)
        SchemaFact.UniqueItems -> stringResource(R.string.doc_fact_unique_items)
        is SchemaFact.Pattern -> stringResource(R.string.doc_fact_pattern, regex)
        is SchemaFact.Example -> stringResource(R.string.doc_fact_example, value)
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

@Composable
private fun MediaTypes(
    item: DocItem.MediaTypes,
    onSelect: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = ScreenPadding + IndentStep * item.depth, end = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(InlineSpacing),
    ) {
        item.contentTypes.forEach { contentType ->
            FilterChip(
                selected = contentType == item.selected,
                onClick = { onSelect(item.ownerId, contentType) },
                label = { Text(contentType, style = MaterialTheme.typography.codeSmall) },
            )
        }
    }
}

@Composable
private fun ExampleBlock(
    item: DocItem.ExampleBlock,
    modifier: Modifier = Modifier,
) {
    Indented(item.depth, modifier.padding(vertical = RowVerticalPadding)) {
        Muted(item.title.text())
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

@Composable
private fun Indented(
    depth: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            modifier.fillMaxWidth().padding(
                start = ScreenPadding + IndentStep * depth + ChevronSize + InlineSpacing,
                end = ScreenPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(LineSpacing),
    ) { content() }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Pill(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = InlineSpacing, vertical = LineSpacing),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun Centered(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 2xx 用主色，3xx 用第三色，4xx / 5xx 用错误色，其余（如 default）用中性色。 */
@Composable
private fun statusColor(status: String): Color =
    when (status.firstOrNull()) {
        '2' -> MaterialTheme.colorScheme.primary
        '3' -> MaterialTheme.colorScheme.tertiary
        '4', '5' -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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

@StringRes
private fun ParameterLocation.label(): Int =
    when (this) {
        ParameterLocation.PATH -> R.string.doc_location_path
        ParameterLocation.QUERY -> R.string.doc_location_query
        ParameterLocation.QUERY_STRING -> R.string.doc_location_query_string
        ParameterLocation.HEADER -> R.string.doc_location_header
        ParameterLocation.COOKIE -> R.string.doc_location_cookie
    }

private const val OVERVIEW_KEY = "overview"
private const val NOT_KEYWORD = "not"
private const val ENUM_SEPARATOR = ", "
private const val COLLAPSED_ROTATION = -90f
private val ScreenPadding = 16.dp
private val IndentStep = 16.dp
private val SectionSpacing = 24.dp
private val GroupSpacing = 12.dp
private val InlineSpacing = 8.dp
private val LineSpacing = 2.dp
private val RowVerticalPadding = 8.dp
private val ChevronSize = 20.dp
private val SmallIcon = 16.dp
private val CodePadding = 12.dp
