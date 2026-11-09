package work.foofish.apinspector.operations

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.spec.SpecSummary
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.FailureDialog
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.SearchField
import work.foofish.apinspector.ui.component.SegmentGap
import work.foofish.apinspector.ui.component.WarningsSheet
import work.foofish.apinspector.ui.component.segmentedShape
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.codeMedium

/**
 * 一份 API 文档的接口列表。
 *
 * @param selected 双栏布局下当前在详情栏中打开的接口，用于高亮。
 * @param onOpenDrawer 非空时（Medium 及以上窗口）导航图标打开 API 文档侧边导航；否则为返回。
 */
@Composable
fun OperationListScreen(
    viewModel: OperationListViewModel,
    selected: OperationKey?,
    onOpenOperation: (OperationKey) -> Unit,
    onBack: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OperationListContent(
        state = state,
        selected = selected,
        onQueryChange = viewModel::onQueryChange,
        onToggleSection = viewModel::toggleSection,
        onSetFavorite = viewModel::setFavorite,
        onOpenOperation = onOpenOperation,
        onRefresh = viewModel::refresh,
        onBack = onBack,
        onOpenDrawer = onOpenDrawer,
        modifier = modifier,
    )
    when (val refresh = state.refresh) {
        is RefreshStatus.ConfirmingRemoval -> {
            ConfirmRemovalDialog(removed = refresh.removed, onConfirm = viewModel::confirmRefresh, onDismiss = viewModel::cancelRefresh)
        }

        is RefreshStatus.Done -> {
            RefreshSummaryDialog(diff = refresh.diff, warningCount = refresh.warningCount, onDismiss = viewModel::onRefreshResultShown)
        }

        is RefreshStatus.Failed -> {
            FailureDialog(
                title = stringResource(R.string.refresh_failed_title),
                failure = refresh.failure,
                onDismiss = viewModel::onRefreshResultShown,
            )
        }

        // 进行中的状态由顶部进度条展示，不弹对话框。
        RefreshStatus.Idle, RefreshStatus.Fetching, RefreshStatus.Applying -> {}
    }
}

@Composable
private fun OperationListContent(
    state: OperationListUiState,
    selected: OperationKey?,
    onQueryChange: (String) -> Unit,
    onToggleSection: (SectionId) -> Unit,
    onSetFavorite: (OperationKey, Boolean) -> Unit,
    onOpenOperation: (OperationKey) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var warningsToShow by remember { mutableStateOf<ImmutableList<ParseWarning>?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            OperationListTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onShowWarnings = { warningsToShow = it },
                onRefresh = onRefresh,
                onBack = onBack,
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { innerPadding ->
        when (val content = state.content) {
            OperationListContent.Loading -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            OperationListContent.Missing -> {
                Message(R.drawable.ic_error, stringResource(R.string.operations_spec_missing), body = null, Modifier.padding(innerPadding))
            }

            is OperationListContent.Failed -> {
                Message(
                    R.drawable.ic_error,
                    stringResource(R.string.operations_load_failed_title),
                    content.failure.message(),
                    Modifier.padding(innerPadding),
                )
            }

            is OperationListContent.Loaded -> {
                if (!content.hasOperations) {
                    Message(R.drawable.ic_api, stringResource(R.string.operations_empty), body = null, Modifier.padding(innerPadding))
                } else {
                    OperationList(
                        sections = content.sections,
                        selected = selected,
                        onQueryChange = onQueryChange,
                        onToggleSection = onToggleSection,
                        onSetFavorite = onSetFavorite,
                        onOpenOperation = onOpenOperation,
                        contentPadding = innerPadding,
                    )
                }
            }
        }
    }
    warningsToShow?.let { WarningsSheet(warnings = it, onDismiss = { warningsToShow = null }) }
}

@Composable
private fun OperationListTopBar(
    state: OperationListUiState,
    scrollBehavior: TopAppBarScrollBehavior,
    onShowWarnings: (ImmutableList<ParseWarning>) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MediumFlexibleTopAppBar(
            title = { Text(state.spec?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = { state.spec?.let { Text(it.subtitle()) } },
            navigationIcon = {
                if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.operations_switch_spec))
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                    }
                }
            },
            actions = {
                val warnings = (state.content as? OperationListContent.Loaded)?.warnings
                if (!warnings.isNullOrEmpty()) {
                    IconButton(onClick = { onShowWarnings(warnings) }) {
                        Icon(painterResource(R.drawable.ic_warning), contentDescription = stringResource(R.string.warnings_title))
                    }
                }
                if (state.canRefresh) {
                    IconButton(onClick = onRefresh, enabled = state.refresh == RefreshStatus.Idle) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            },
            scrollBehavior = scrollBehavior,
        )
        if (state.refresh == RefreshStatus.Fetching || state.refresh == RefreshStatus.Applying) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OperationList(
    sections: ImmutableList<OperationSection>,
    selected: OperationKey?,
    onQueryChange: (String) -> Unit,
    onToggleSection: (SectionId) -> Unit,
    onSetFavorite: (OperationKey, Boolean) -> Unit,
    onOpenOperation: (OperationKey) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = ListBottomPadding),
        verticalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        item(key = SEARCH_KEY) {
            SearchField(
                placeholder = stringResource(R.string.operations_search_placeholder),
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = SectionSpacing),
            )
        }
        if (sections.isEmpty()) {
            item(key = NO_MATCH_KEY) {
                Text(
                    text = stringResource(R.string.operations_no_match),
                    modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        sections.forEach { section ->
            section(section, selected, onToggleSection, onSetFavorite, onOpenOperation)
        }
    }
}

private fun LazyListScope.section(
    section: OperationSection,
    selected: OperationKey?,
    onToggleSection: (SectionId) -> Unit,
    onSetFavorite: (OperationKey, Boolean) -> Unit,
    onOpenOperation: (OperationKey) -> Unit,
) {
    val sectionKey = section.id.lazyKey()
    item(key = sectionKey) {
        SectionHeader(section = section, onToggle = { onToggleSection(section.id) }, modifier = Modifier.animateItem())
    }
    if (section.collapsed) return
    itemsIndexed(section.operations, key = { _, row -> "$sectionKey ${row.key}" }) { index, row ->
        OperationListItem(
            row = row,
            selected = row.key == selected,
            onClick = { onOpenOperation(row.key) },
            onSetFavorite = { onSetFavorite(row.key, it) },
            modifier =
                Modifier
                    .animateItem()
                    .padding(horizontal = ScreenPadding)
                    .clip(segmentedShape(index, section.operations.size)),
        )
    }
}

@Composable
private fun SectionHeader(
    section: OperationSection,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (section.collapsed) COLLAPSED_ROTATION else 0f, label = "section-chevron")
    ListItem(
        onClick = onToggle,
        modifier = modifier.padding(top = SectionSpacing),
        supportingContent = section.tag?.description?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (section.collapsed) R.string.action_expand else R.string.action_collapse),
                modifier = Modifier.rotate(rotation),
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Text(
            text = "${section.title()}  ${section.operations.size}",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun OperationListItem(
    row: OperationRow,
    selected: Boolean,
    onClick: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier,
        leadingContent = { MethodBadge(row.key.method) },
        supportingContent = row.summary?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            IconToggleButton(checked = row.favorite, onCheckedChange = onSetFavorite) {
                Icon(
                    painter = painterResource(if (row.favorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = stringResource(if (row.favorite) R.string.operations_unfavorite else R.string.operations_favorite),
                )
            }
        },
        colors =
            ListItemDefaults.colors(
                containerColor =
                    with(MaterialTheme.colorScheme) { if (selected) secondaryContainer else surfaceContainerLow },
            ),
    ) {
        Text(
            text = row.key.path,
            style = MaterialTheme.typography.codeMedium,
            textDecoration = if (row.deprecated) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun OperationSection.title(): String =
    when (val id = id) {
        SectionId.Favorites -> stringResource(R.string.operations_section_favorites)
        is SectionId.Tagged -> tag?.summary ?: id.name
        SectionId.Untagged -> stringResource(R.string.operations_section_untagged)
    }

private fun SectionId.lazyKey(): String =
    when (this) {
        SectionId.Favorites -> "favorites"
        is SectionId.Tagged -> "tag:$name"
        SectionId.Untagged -> "untagged"
    }

/** 例如"v1.0 · 24 个接口"。 */
@Composable
private fun SpecSummary.subtitle(): String =
    listOfNotNull(
        version?.let { stringResource(R.string.spec_version, it) },
        pluralStringResource(R.plurals.spec_operation_count, operationCount, operationCount),
    ).joinToString(stringResource(R.string.spec_info_separator))

@Composable
private fun Message(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(icon = painterResource(icon), title = title, body = body)
    }
}

/** 双栏布局下尚未选中接口时的详情栏占位。 */
@Composable
fun NoOperationSelectedPane(modifier: Modifier = Modifier) {
    Message(
        icon = R.drawable.ic_description,
        title = stringResource(R.string.operations_no_operation_selected_title),
        body = stringResource(R.string.operations_no_operation_selected_body),
        modifier = modifier,
    )
}

private const val SEARCH_KEY = "search"
private const val NO_MATCH_KEY = "no-match"
private const val COLLAPSED_ROTATION = -90f
private val ScreenPadding = 16.dp
private val SectionSpacing = 8.dp
private val ListBottomPadding = 24.dp
