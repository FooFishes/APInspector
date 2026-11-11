package work.foofish.apinspector.operations

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestsViewModel
import work.foofish.apinspector.custom.ui.CustomRequestDialogs
import work.foofish.apinspector.custom.ui.customRequestSection
import work.foofish.apinspector.custom.ui.matching
import work.foofish.apinspector.custom.ui.rememberCustomRequestsHost
import work.foofish.apinspector.environment.EnvironmentId
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.request.ui.EnvironmentSelector
import work.foofish.apinspector.spec.SpecSummary
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.FailureDialog
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.MethodBadge
import work.foofish.apinspector.ui.component.SearchField
import work.foofish.apinspector.ui.component.WarningsSheet
import work.foofish.apinspector.ui.text.message
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.codeMedium
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors

/**
 * 一份 API 文档的接口列表。
 *
 * @param selected 双栏布局下当前在详情栏中打开的接口，用于高亮。
 * @param onOpenDrawer 非空时（Medium 及以上窗口）导航图标打开 API 文档侧边导航；否则为返回。
 */
@Composable
fun OperationListScreen(
    viewModel: OperationListViewModel,
    customs: CustomRequestsViewModel,
    selected: OperationKey?,
    selectedCustom: CustomRequestId?,
    onOpenOperation: (OperationKey) -> Unit,
    onOpenCustom: (CustomRequestId) -> Unit,
    onOpenHistory: () -> Unit,
    onManageEnvironments: () -> Unit,
    onBack: () -> Unit,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val customState by customs.uiState.collectAsStateWithLifecycle()
    val host = rememberCustomRequestsHost(customs, onOpenCustom)
    val requests = remember(customState.requests, state.query) { customState.requests.orEmpty().matching(state.query) }
    OperationListContent(
        state = state,
        selected = selected,
        customSection = { customRequestSection(requests, selectedCustom, host, onOpenCustom) },
        onQueryChange = viewModel::onQueryChange,
        onToggleSection = viewModel::toggleSection,
        onSetFavorite = viewModel::setFavorite,
        onOpenOperation = onOpenOperation,
        bar =
            OperationListBarActions(
                onRefresh = viewModel::refresh,
                onOpenHistory = onOpenHistory,
                onSelectEnvironment = viewModel::selectEnvironment,
                onManageEnvironments = onManageEnvironments,
                onBack = onBack,
                onOpenDrawer = onOpenDrawer,
            ),
        modifier = modifier,
    )
    CustomRequestDialogs(host, customState.moveTargets)
    when (val refresh = state.refresh) {
        is RefreshStatus.ConfirmingRemoval -> {
            ConfirmRemovalDialog(
                removed = refresh.removed,
                withPresets = refresh.withPresets,
                onConfirm = viewModel::confirmRefresh,
                onDismiss = viewModel::cancelRefresh,
            )
        }

        is RefreshStatus.Done -> {
            RefreshSummaryDialog(
                diff = refresh.diff,
                warningCount = refresh.warningCount,
                serversChanged = refresh.serversChanged,
                onDismiss = viewModel::onRefreshResultShown,
            )
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
    customSection: LazyListScope.() -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleSection: (SectionId) -> Unit,
    onSetFavorite: (OperationKey, Boolean) -> Unit,
    onOpenOperation: (OperationKey) -> Unit,
    bar: OperationListBarActions,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var warningsToShow by remember { mutableStateOf<ImmutableList<ParseWarning>?>(null) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.screen,
        topBar = {
            OperationListTopBar(
                state = state,
                scrollBehavior = scrollBehavior,
                onShowWarnings = { warningsToShow = it },
                actions = bar,
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
                OperationList(
                    sections = content.sections,
                    // 文档本身没有接口时，仍然可以在这里建自定义请求。
                    emptyText = stringResource(if (content.hasOperations) R.string.operations_no_match else R.string.operations_empty),
                    selected = selected,
                    customSection = customSection,
                    onQueryChange = onQueryChange,
                    onToggleSection = onToggleSection,
                    onSetFavorite = onSetFavorite,
                    onOpenOperation = onOpenOperation,
                    contentPadding = innerPadding,
                )
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
    actions: OperationListBarActions,
    modifier: Modifier = Modifier,
) {
    val onOpenDrawer = actions.onOpenDrawer
    Column(modifier = modifier) {
        // 小号顶栏：标题一行放不下时截断，把首屏留给接口列表。
        TopAppBar(
            title = { Text(state.spec?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = { state.spec?.let { Text(it.subtitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.operations_switch_spec))
                    }
                } else {
                    BackButton(onClick = actions.onBack)
                }
            },
            actions = {
                IconButton(onClick = actions.onOpenHistory) {
                    Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.history_title))
                }
                val warnings = (state.content as? OperationListContent.Loaded)?.warnings
                if (!warnings.isNullOrEmpty()) {
                    IconButton(onClick = { onShowWarnings(warnings) }) {
                        Icon(painterResource(R.drawable.ic_warning), contentDescription = stringResource(R.string.warnings_title))
                    }
                }
                if (state.canRefresh) {
                    IconButton(onClick = actions.onRefresh, enabled = state.refresh == RefreshStatus.Idle) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                }
            },
            colors = screenTopAppBarColors(),
            scrollBehavior = scrollBehavior,
        )
        if (state.content is OperationListContent.Loaded) {
            EnvironmentSelector(
                environments = state.environments,
                current = state.environment,
                onSelect = actions.onSelectEnvironment,
                onManage = actions.onManageEnvironments,
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = SectionSpacing),
            )
        }
        if (state.refresh == RefreshStatus.Fetching || state.refresh == RefreshStatus.Applying) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 顶栏上的操作。 */
private class OperationListBarActions(
    val onRefresh: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onSelectEnvironment: (EnvironmentId) -> Unit,
    val onManageEnvironments: () -> Unit,
    val onBack: () -> Unit,
    /** 非空时（Medium 及以上窗口）导航图标打开 API 文档侧边导航；否则为返回。 */
    val onOpenDrawer: (() -> Unit)?,
)

@Composable
private fun OperationList(
    sections: ImmutableList<OperationSection>,
    emptyText: String,
    selected: OperationKey?,
    customSection: LazyListScope.() -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item(key = SEARCH_KEY) {
            SearchField(
                placeholder = stringResource(R.string.operations_search_placeholder),
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = SectionSpacing),
            )
        }
        customSection()
        if (sections.isEmpty()) {
            item(key = NO_MATCH_KEY) {
                Text(
                    text = emptyText,
                    modifier = Modifier.fillMaxWidth().padding(NoMatchPadding),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
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
    // 分组标题吸顶：长分组里滚动时始终知道自己在哪个 tag 下，也随时可以折叠它。
    stickyHeader(key = sectionKey) {
        SectionHeader(section = section, onToggle = { onToggleSection(section.id) })
    }
    if (section.collapsed) return
    itemsIndexed(section.operations, key = { _, row -> "$sectionKey ${row.key}" }) { index, row ->
        OperationListItem(
            row = row,
            shapes = ListItemDefaults.segmentedShapes(index, section.operations.size),
            selected = row.key == selected,
            onClick = { onOpenOperation(row.key) },
            onSetFavorite = { onSetFavorite(row.key, it) },
            modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
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
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.screen)
                .padding(horizontal = HeaderInset, vertical = HeaderVerticalPadding)
                .clip(CircleShape)
                .clickable(onClick = onToggle)
                .padding(horizontal = HeaderContentPadding, vertical = HeaderContentPaddingVertical),
        horizontalArrangement = Arrangement.spacedBy(HeaderSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = section.title(),
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        CountPill(section.operations.size)
        section.tag?.description?.let {
            Text(
                text = it,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } ?: Box(Modifier.weight(1f))
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = stringResource(if (section.collapsed) R.string.action_expand else R.string.action_collapse),
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分组内的接口数。 */
@Composable
private fun CountPill(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = PillPaddingHorizontal, vertical = PillPaddingVertical),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun OperationListItem(
    row: OperationRow,
    shapes: ListItemShapes,
    selected: Boolean,
    onClick: () -> Unit,
    onSetFavorite: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 组件的两行高度固定为 72dp；这里收紧到 64dp（随字体缩放放大），一屏多看一两个接口。
    val maxHeight = ItemHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = shapes,
        modifier = modifier.heightIn(max = maxHeight),
        leadingContent = { MethodBadge(row.key.method) },
        verticalAlignment = Alignment.CenterVertically,
        supportingContent = row.summary?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            IconToggleButton(checked = row.favorite, onCheckedChange = onSetFavorite) {
                Icon(
                    painter = painterResource(if (row.favorite) R.drawable.ic_star_filled else R.drawable.ic_star),
                    contentDescription = stringResource(if (row.favorite) R.string.operations_unfavorite else R.string.operations_favorite),
                )
            }
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.card),
        contentPadding = PaddingValues(horizontal = ItemPaddingHorizontal, vertical = ItemPaddingVertical),
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
private val NoMatchPadding = 32.dp
private val SectionSpacing = 8.dp
private val HeaderInset = 8.dp
private val HeaderVerticalPadding = 4.dp
private val HeaderContentPadding = 12.dp
private val HeaderContentPaddingVertical = 8.dp
private val ItemPaddingHorizontal = 16.dp
private val ItemPaddingVertical = 4.dp
private val ItemHeight = 64.dp
private val HeaderSpacing = 8.dp
private val PillPaddingHorizontal = 8.dp
private val PillPaddingVertical = 2.dp
private val ListBottomPadding = 24.dp
