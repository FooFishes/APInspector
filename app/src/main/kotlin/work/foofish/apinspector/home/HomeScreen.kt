package work.foofish.apinspector.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.ParseWarning
import work.foofish.apinspector.spec.ImportOutcome
import work.foofish.apinspector.spec.ImportSource
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.spec.SpecOrigin
import work.foofish.apinspector.spec.SpecSummary
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.FailureDialog
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.component.SearchField
import work.foofish.apinspector.ui.component.WarningsSheet
import work.foofish.apinspector.ui.text.relativeTime
import work.foofish.apinspector.ui.theme.APInspectorTheme
import work.foofish.apinspector.ui.theme.ScreenPadding
import work.foofish.apinspector.ui.theme.card
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors
import java.time.Instant

@Composable
fun HomeScreen(
    onOpenSpec: (SpecId) -> Unit,
    onOpenScratch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onImport = viewModel::import,
        onImportOutcomeConsume = viewModel::onImportOutcomeShown,
        onOpenSpec = onOpenSpec,
        onOpenScratch = onOpenScratch,
        onPinChange = viewModel::setPinned,
        onDelete = viewModel::delete,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/** 导入对话框 / 面板。 */
private enum class ImportPrompt { Url, Text }

@Composable
private fun HomeContent(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onImport: (ImportSource) -> Unit,
    onImportOutcomeConsume: () -> Unit,
    onOpenSpec: (SpecId) -> Unit,
    onOpenScratch: () -> Unit,
    onPinChange: (SpecId, Boolean) -> Unit,
    onDelete: (SpecId) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var prompt by rememberSaveable { mutableStateOf<ImportPrompt?>(null) }
    var pendingDeletion by remember { mutableStateOf<SpecSummary?>(null) }
    var warningsToShow by remember { mutableStateOf<ImmutableList<ParseWarning>?>(null) }

    ImportOutcomeEffect(
        status = state.importStatus,
        snackbarHostState = snackbarHostState,
        onConsume = onImportOutcomeConsume,
        onOpenSpec = onOpenSpec,
        onShowWarnings = { warningsToShow = it },
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { HomeTopBar(state.importStatus == ImportStatus.Importing, scrollBehavior, onOpenSettings) },
        containerColor = MaterialTheme.colorScheme.screen,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ImportMenu(
                enabled = state.importStatus != ImportStatus.Importing,
                onImportFile = onImport,
                onEnterUrl = { prompt = ImportPrompt.Url },
                onPasteText = { prompt = ImportPrompt.Text },
            )
        },
    ) { innerPadding ->
        HomeBody(
            state = state,
            onQueryChange = onQueryChange,
            onOpenSpec = onOpenSpec,
            onOpenScratch = onOpenScratch,
            onPinChange = onPinChange,
            onRequestDelete = { pendingDeletion = it },
            contentPadding = innerPadding,
        )
    }

    prompt?.let {
        ImportPromptHost(
            prompt = it,
            onImport = { source ->
                prompt = null
                onImport(source)
            },
            onDismiss = { prompt = null },
        )
    }
    ImportFailureDialog(state.importStatus, onDismiss = onImportOutcomeConsume)
    pendingDeletion?.let { spec ->
        DeleteSpecDialog(
            spec = spec,
            onConfirm = {
                pendingDeletion = null
                onDelete(spec.id)
            },
            onDismiss = { pendingDeletion = null },
        )
    }
    warningsToShow?.let { WarningsSheet(warnings = it, onDismiss = { warningsToShow = null }) }
}

/** 导入失败时说明原因。 */
@Composable
private fun ImportFailureDialog(
    status: ImportStatus,
    onDismiss: () -> Unit,
) {
    val failed = (status as? ImportStatus.Finished)?.outcome as? ImportOutcome.Failed ?: return
    FailureDialog(title = stringResource(R.string.import_failed_title), failure = failed.failure, onDismiss = onDismiss)
}

@Composable
private fun HomeTopBar(
    importing: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        LargeFlexibleTopAppBar(
            title = { Text(stringResource(R.string.app_name)) },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                }
            },
            colors = screenTopAppBarColors(),
            scrollBehavior = scrollBehavior,
        )
        if (importing) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HomeBody(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onOpenSpec: (SpecId) -> Unit,
    onOpenScratch: () -> Unit,
    onPinChange: (SpecId, Boolean) -> Unit,
    onRequestDelete: (SpecSummary) -> Unit,
    contentPadding: PaddingValues,
) {
    val specs = state.specs
    when {
        specs == null -> {
            LoadingState(Modifier.padding(contentPadding))
        }

        !state.hasSpecs -> {
            // 还没有 API 文档时，"临时请求"入口照样固定在最上面（PRD 2）。
            Column(Modifier.padding(contentPadding)) {
                ScratchEntry(onClick = onOpenScratch, modifier = Modifier.padding(horizontal = ScreenPadding))
                CenteredEmptyState(Modifier.weight(1f))
            }
        }

        else -> {
            SpecList(specs, onQueryChange, onOpenSpec, onOpenScratch, onPinChange, onRequestDelete, contentPadding)
        }
    }
}

@Composable
private fun ImportPromptHost(
    prompt: ImportPrompt,
    onImport: (ImportSource) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        ImportPrompt.Url -> UrlImportDialog(onImport = { onImport(ImportSource.Url(it)) }, onDismiss = onDismiss)
        ImportPrompt.Text -> TextImportSheet(onImport = { onImport(ImportSource.Text(it)) }, onDismiss = onDismiss)
    }
}

/** 导入成功后以 Snackbar 提示；有警告时提供"查看"，否则提供"打开"。失败由对话框展示。 */
@Composable
private fun ImportOutcomeEffect(
    status: ImportStatus,
    snackbarHostState: SnackbarHostState,
    onConsume: () -> Unit,
    onOpenSpec: (SpecId) -> Unit,
    onShowWarnings: (ImmutableList<ParseWarning>) -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val currentOnConsume by rememberUpdatedState(onConsume)
    val currentOnOpenSpec by rememberUpdatedState(onOpenSpec)
    val currentOnShowWarnings by rememberUpdatedState(onShowWarnings)
    LaunchedEffect(status) {
        val imported = (status as? ImportStatus.Finished)?.outcome as? ImportOutcome.Imported ?: return@LaunchedEffect
        currentOnConsume()
        // 在独立的协程中等待 Snackbar：上面的 currentOnConsume 会改变 status，从而取消本 effect。
        scope.launch {
            val warnings = imported.warnings
            val message =
                if (warnings.isEmpty()) {
                    resources.getString(R.string.import_succeeded, imported.title)
                } else {
                    resources.getQuantityString(R.plurals.import_succeeded_with_warnings, warnings.size, imported.title, warnings.size)
                }
            val action = resources.getString(if (warnings.isEmpty()) R.string.import_open else R.string.import_view_warnings)
            if (snackbarHostState.showSnackbar(message, actionLabel = action) == SnackbarResult.ActionPerformed) {
                if (warnings.isEmpty()) currentOnOpenSpec(imported.id) else currentOnShowWarnings(warnings.toImmutableList())
            }
        }
    }
}

@Composable
private fun ImportMenu(
    enabled: Boolean,
    onImportFile: (ImportSource) -> Unit,
    onEnterUrl: () -> Unit,
    onPasteText: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }
    val pickFile =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { onImportFile(ImportSource.File(it)) } }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = { if (enabled) expanded = it }) {
                Icon(
                    painter = painterResource(if (checkedProgress > HALF_PROGRESS) R.drawable.ic_close else R.drawable.ic_add),
                    contentDescription = stringResource(R.string.home_import_menu),
                    modifier = Modifier.animateIcon({ checkedProgress }),
                )
            }
        },
    ) {
        val items =
            listOf(
                Triple(R.drawable.ic_upload_file, R.string.import_from_file, { pickFile.launch(SPEC_MIME_TYPES) }),
                Triple(R.drawable.ic_link, R.string.import_from_url, onEnterUrl),
                Triple(R.drawable.ic_content_paste, R.string.import_from_text, onPasteText),
            )
        items.forEach { (icon, label, onClick) ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onClick()
                },
                icon = { Icon(painterResource(icon), contentDescription = null) },
                text = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun SpecList(
    specs: ImmutableList<SpecSummary>,
    onQueryChange: (String) -> Unit,
    onOpenSpec: (SpecId) -> Unit,
    onOpenScratch: () -> Unit,
    onPinChange: (SpecId, Boolean) -> Unit,
    onRequestDelete: (SpecSummary) -> Unit,
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
                placeholder = stringResource(R.string.home_search_placeholder),
                onQueryChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = SearchSpacing),
            )
        }
        item(key = SCRATCH_KEY) {
            ScratchEntry(onClick = onOpenScratch, modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = SearchSpacing))
        }
        if (specs.isEmpty()) {
            item(key = NO_MATCH_KEY) {
                Text(
                    text = stringResource(R.string.home_no_match),
                    modifier = Modifier.fillMaxWidth().padding(NoMatchPadding),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        itemsIndexed(specs, key = { _, spec -> spec.id.value }) { index, spec ->
            SpecListItem(
                spec = spec,
                shapes = ListItemDefaults.segmentedShapes(index, specs.size),
                onClick = { onOpenSpec(spec.id) },
                onPinChange = { onPinChange(spec.id, it) },
                onRequestDelete = { onRequestDelete(spec) },
                modifier = Modifier.animateItem().padding(horizontal = ScreenPadding),
            )
        }
    }
}

/** 固定置顶的"临时请求"入口（PRD 2）：不可删除，不参与排序与搜索。 */
@Composable
private fun ScratchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        modifier = modifier,
        leadingContent = { Icon(painterResource(R.drawable.ic_send), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        supportingContent = { Text(stringResource(R.string.scratch_description), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(stringResource(R.string.scratch_title), style = MaterialTheme.typography.titleMediumEmphasized)
    }
}

@Composable
private fun SpecListItem(
    spec: SpecSummary,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onPinChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按与「更多」按钮打开同一个菜单。
    var menuExpanded by remember { mutableStateOf(false) }
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        onLongClick = { menuExpanded = true },
        onLongClickLabel = stringResource(R.string.action_more),
        leadingContent = { OriginIcon(spec.origin) },
        supportingContent = { Text(spec.describe(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spec.pinned) {
                    Icon(
                        painter = painterResource(R.drawable.ic_push_pin_filled),
                        contentDescription = stringResource(R.string.home_pinned),
                        modifier = Modifier.size(PinIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                SpecMenu(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    pinned = spec.pinned,
                    onPinChange = onPinChange,
                    onRequestDelete = onRequestDelete,
                )
            }
        },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.card),
    ) {
        Text(spec.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OriginIcon(
    origin: SpecOrigin,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (origin) {
            is SpecOrigin.File -> R.drawable.ic_upload_file
            is SpecOrigin.Url -> R.drawable.ic_link
            SpecOrigin.Pasted -> R.drawable.ic_content_paste
        }
    Box(
        modifier =
            modifier
                .size(OriginIconContainerSize)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie6Sided.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(OriginIconSize),
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun SpecMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    pinned: Boolean,
    onPinChange: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, shape = MaterialTheme.shapes.large) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) R.string.home_unpin else R.string.home_pin)) },
                leadingIcon = {
                    Icon(
                        painterResource(if (pinned) R.drawable.ic_push_pin else R.drawable.ic_push_pin_filled),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onPinChange(!pinned)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onRequestDelete()
                },
            )
        }
    }
}

/** 例如"v1.0 · 24 个接口 · URL · 3 天前"。 */
@Composable
private fun SpecSummary.describe(): String {
    val parts =
        listOfNotNull(
            version?.let { stringResource(R.string.spec_version, it) },
            pluralStringResource(R.plurals.spec_operation_count, operationCount, operationCount),
            when (val origin = origin) {
                is SpecOrigin.File -> {
                    origin.displayName?.let { stringResource(R.string.spec_origin_file_named, it) }
                        ?: stringResource(R.string.spec_origin_file)
                }

                is SpecOrigin.Url -> {
                    stringResource(R.string.spec_origin_url)
                }

                SpecOrigin.Pasted -> {
                    stringResource(R.string.spec_origin_pasted)
                }
            },
            relativeTime(updatedAt),
        )
    return parts.joinToString(stringResource(R.string.spec_info_separator))
}

@Composable
private fun DeleteSpecDialog(
    spec: SpecSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.home_delete_title, spec.title)) },
        text = { Text(stringResource(R.string.home_delete_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CenteredEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = painterResource(R.drawable.ic_api),
            title = stringResource(R.string.home_empty_title),
            body = stringResource(R.string.home_empty_body),
        )
    }
}

/** 双栏布局下尚未选中 API 文档时的详情栏占位。 */
@Composable
fun NoSpecSelectedPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = painterResource(R.drawable.ic_list_alt),
            title = stringResource(R.string.home_no_spec_selected_title),
            body = stringResource(R.string.home_no_spec_selected_body),
        )
    }
}

/** 文件选择器可选的类型：YAML 没有统一的 MIME 类型，部分文件管理器会把 .yaml 标成纯文本或二进制。 */
private val SPEC_MIME_TYPES =
    arrayOf(
        "application/json",
        "application/yaml",
        "application/x-yaml",
        "text/yaml",
        "text/x-yaml",
        "text/plain",
        "application/octet-stream",
    )

private const val SEARCH_KEY = "search"
private const val NO_MATCH_KEY = "no-match"
private const val HALF_PROGRESS = 0.5f
private val NoMatchPadding = 32.dp
private val SearchSpacing = 12.dp
private const val SCRATCH_KEY = "scratch"
private val ListBottomPadding = 96.dp
private val PinIconSize = 18.dp
private val OriginIconContainerSize = 44.dp
private val OriginIconSize = 22.dp

@PreviewLightDark
@Composable
private fun HomeContentPreview() {
    APInspectorTheme {
        HomeContent(
            state =
                HomeUiState(
                    specs =
                        persistentListOf(
                            SpecSummary(SpecId("1"), "Petstore", "1.0.0", SpecOrigin.Url("https://example.com"), 12, true, Instant.EPOCH),
                            SpecSummary(SpecId("2"), "Billing", null, SpecOrigin.Pasted, 3, false, Instant.EPOCH),
                        ),
                    hasSpecs = true,
                    query = "",
                    importStatus = ImportStatus.Idle,
                ),
            onQueryChange = {},
            onImport = {},
            onImportOutcomeConsume = {},
            onOpenSpec = {},
            onPinChange = { _, _ -> },
            onDelete = {},
            onOpenScratch = {},
            onOpenSettings = {},
        )
    }
}
