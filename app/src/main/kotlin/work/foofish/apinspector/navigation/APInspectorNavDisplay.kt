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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.launch
import work.foofish.apinspector.home.HomeScreen
import work.foofish.apinspector.home.NoSpecSelectedPane
import work.foofish.apinspector.operationdoc.OperationDocScreen
import work.foofish.apinspector.operationdoc.OperationDocViewModel
import work.foofish.apinspector.operations.NoOperationSelectedPane
import work.foofish.apinspector.operations.OperationListScreen
import work.foofish.apinspector.operations.OperationListViewModel

/**
 * 应用的导航宿主（PRD 1.2）。
 *
 * 返回栈由应用持有并跨配置变更保存。[ListDetailSceneStrategy] 在 Compact 窗口下逐页显示，
 * 在 Medium 及以上窗口把"接口列表 | 接口文档"（或"首页 | 占位"）合并为双栏；
 * 此时首页不再可见，API 文档改由侧边导航切换。
 */
@Composable
fun APInspectorNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    val multiPane = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentSpec = backStack.currentSpec

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
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(rememberListDetailSceneStrategy()),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider {
                    entry<HomeKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSpecSelectedPane() })) {
                        HomeScreen(onOpenSpec = backStack::openSpec)
                    }
                    entry<SpecKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoOperationSelectedPane() })) { key ->
                        OperationListScreen(
                            viewModel =
                                hiltViewModel<OperationListViewModel, OperationListViewModel.Factory>(
                                    creationCallback = { factory -> factory.create(key) },
                                ),
                            selected = backStack.currentOperation?.takeIf { it.specId == key.specId }?.operation,
                            onOpenOperation = { backStack.openOperation(OperationDocKey(key.specId, it)) },
                            onBack = { backStack.removeLastOrNull() },
                            onOpenDrawer = if (multiPane) ({ scope.launch { drawerState.open() } }) else null,
                        )
                    }
                    entry<OperationDocKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                        OperationDocScreen(
                            viewModel =
                                hiltViewModel<OperationDocViewModel, OperationDocViewModel.Factory>(
                                    creationCallback = { factory -> factory.create(key) },
                                ),
                            showBack = !multiPane,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
        )
    }
}
