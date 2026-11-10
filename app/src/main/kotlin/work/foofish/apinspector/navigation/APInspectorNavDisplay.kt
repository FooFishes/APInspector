package work.foofish.apinspector.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import work.foofish.apinspector.environment.ui.EnvironmentScreen
import work.foofish.apinspector.environment.ui.EnvironmentViewModel
import work.foofish.apinspector.environment.ui.EnvironmentsScreen
import work.foofish.apinspector.environment.ui.EnvironmentsViewModel
import work.foofish.apinspector.environment.ui.NoEnvironmentSelectedPane
import work.foofish.apinspector.history.ui.HistoryEntryScreen
import work.foofish.apinspector.history.ui.HistoryEntryViewModel
import work.foofish.apinspector.history.ui.HistoryScreen
import work.foofish.apinspector.history.ui.HistoryViewModel
import work.foofish.apinspector.history.ui.NoHistoryEntrySelectedPane
import work.foofish.apinspector.home.HomeScreen
import work.foofish.apinspector.home.NoSpecSelectedPane
import work.foofish.apinspector.operation.OperationScreen
import work.foofish.apinspector.operationdoc.OperationDocTab
import work.foofish.apinspector.operationdoc.OperationDocViewModel
import work.foofish.apinspector.operations.NoOperationSelectedPane
import work.foofish.apinspector.operations.OperationListScreen
import work.foofish.apinspector.operations.OperationListViewModel
import work.foofish.apinspector.request.RequestViewModel
import work.foofish.apinspector.request.ui.RequestTab
import work.foofish.apinspector.response.ResponseViewModel
import work.foofish.apinspector.response.ui.ResponseTab
import work.foofish.apinspector.settings.ui.SettingsScreen

/**
 * 应用的导航宿主（PRD 1.2）。
 *
 * 返回栈由应用持有并跨配置变更保存。[ListDetailSceneStrategy] 在 Compact 窗口下逐页显示，
 * 在 Medium 及以上窗口把相邻的"列表 | 详情"（接口列表 | 接口详情、请求记录 | 记录详情、环境列表 | 环境编辑）
 * 合并为双栏；此时首页不再可见，API 文档改由侧边导航切换。设置页不参与双栏。
 */
@Composable
fun APInspectorNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val multiPane = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentSpec = backStack.currentSpec
    val navigation = Navigation(backStack, multiPane, onOpenDrawer = { scope.launch { drawerState.open() } })

    SpecDrawer(
        viewModel = hiltViewModel(),
        drawerState = drawerState,
        enabled = multiPane && currentSpec != null,
        selected = currentSpec?.specId,
        onSelect = { id ->
            backStack.openSpec(id)
            scope.launch { drawerState.close() }
        },
        modifier = modifier,
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = navigation::back,
            sceneStrategies = listOf(rememberListDetailSceneStrategy()),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    homeEntries(navigation)
                    specEntries(navigation)
                    historyEntries(navigation)
                    environmentEntries(navigation)
                },
        )
    }
}

/**
 * 各页面共用的导航状态。
 *
 * @property multiPane Medium 及以上窗口：列表与详情并排，详情页不显示返回按钮。
 */
private class Navigation(
    val backStack: MutableList<NavKey>,
    val multiPane: Boolean,
    private val onOpenDrawer: () -> Unit,
) {
    fun back() {
        backStack.removeLastOrNull()
    }

    /** 双栏布局下导航图标打开 API 文档侧边导航；单栏时为 `null`（返回）。 */
    val openDrawer: (() -> Unit)? get() = onOpenDrawer.takeIf { multiPane }
}

private fun EntryProviderScope<NavKey>.homeEntries(navigation: Navigation) {
    val backStack = navigation.backStack
    entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSpecSelectedPane() })) {
        HomeScreen(onOpenSpec = backStack::openSpec, onOpenSettings = backStack::openSettings)
    }
    entry<SettingsKey> {
        SettingsScreen(viewModel = hiltViewModel(), onBack = navigation::back)
    }
}

private fun EntryProviderScope<NavKey>.specEntries(navigation: Navigation) {
    val backStack = navigation.backStack
    entry<SpecKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoOperationSelectedPane() })) { key ->
        OperationListScreen(
            viewModel = hiltViewModel<OperationListViewModel, OperationListViewModel.Factory>(creationCallback = { it.create(key) }),
            selected = backStack.currentOperation?.takeIf { it.specId == key.specId }?.operation,
            onOpenOperation = { backStack.openOperation(OperationPageKey(key.specId, it)) },
            onOpenHistory = { backStack.openHistory(HistoryKey(key.specId, operation = null)) },
            onManageEnvironments = { backStack.openEnvironments(EnvironmentsKey(key.specId)) },
            onBack = navigation::back,
            onOpenDrawer = navigation.openDrawer,
        )
    }
    entry<OperationPageKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        OperationScreen(
            operation = key.operation,
            showBack = !navigation.multiPane,
            onBack = navigation::back,
            docTab = { modifier ->
                OperationDocTab(
                    hiltViewModel<OperationDocViewModel, OperationDocViewModel.Factory>(creationCallback = { it.create(key) }),
                    modifier,
                )
            },
            requestTab = { onSend, modifier ->
                RequestTab(
                    viewModel = hiltViewModel<RequestViewModel, RequestViewModel.Factory>(creationCallback = { it.create(key) }),
                    onSend = onSend,
                    onManageEnvironments = { backStack.openEnvironments(EnvironmentsKey(key.specId)) },
                    modifier = modifier,
                )
            },
            responseTab = { onRestore, modifier ->
                ResponseTab(
                    viewModel = hiltViewModel<ResponseViewModel, ResponseViewModel.Factory>(creationCallback = { it.create(key) }),
                    onRestore = onRestore,
                    onOpenHistory = { backStack.openHistory(HistoryKey(key.specId, key.operation)) },
                    modifier = modifier,
                )
            },
        )
    }
}

private fun EntryProviderScope<NavKey>.historyEntries(navigation: Navigation) {
    val backStack = navigation.backStack
    entry<HistoryKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoHistoryEntrySelectedPane() })) { key ->
        HistoryScreen(
            viewModel = hiltViewModel<HistoryViewModel, HistoryViewModel.Factory>(creationCallback = { it.create(key) }),
            selected = (backStack.lastOrNull() as? HistoryEntryKey)?.entry,
            onOpen = { backStack.openHistoryEntry(HistoryEntryKey(key.specId, it.value)) },
            onShowAll = { backStack.openHistory(HistoryKey(key.specId, operation = null)) },
            onBack = navigation::back,
        )
    }
    entry<HistoryEntryKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        HistoryEntryScreen(
            viewModel = hiltViewModel<HistoryEntryViewModel, HistoryEntryViewModel.Factory>(creationCallback = { it.create(key) }),
            showBack = !navigation.multiPane,
            onBack = navigation::back,
        )
    }
}

private fun EntryProviderScope<NavKey>.environmentEntries(navigation: Navigation) {
    val backStack = navigation.backStack
    entry<EnvironmentsKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoEnvironmentSelectedPane() })) { key ->
        EnvironmentsScreen(
            viewModel = hiltViewModel<EnvironmentsViewModel, EnvironmentsViewModel.Factory>(creationCallback = { it.create(key) }),
            selected = (backStack.lastOrNull() as? EnvironmentKey)?.environmentId,
            onOpen = { backStack.openEnvironment(EnvironmentKey(key.specId, it)) },
            onBack = navigation::back,
        )
    }
    entry<EnvironmentKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        EnvironmentScreen(
            viewModel = hiltViewModel<EnvironmentViewModel, EnvironmentViewModel.Factory>(creationCallback = { it.create(key) }),
            showBack = !navigation.multiPane,
            onBack = navigation::back,
        )
    }
}
