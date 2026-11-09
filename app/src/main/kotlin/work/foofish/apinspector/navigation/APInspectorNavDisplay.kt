package work.foofish.apinspector.navigation

import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import work.foofish.apinspector.home.HomeKey
import work.foofish.apinspector.home.HomeScreen
import work.foofish.apinspector.home.NoSpecSelectedPane

/**
 * 应用的导航宿主（PRD 1.2）。
 *
 * 返回栈由应用持有并可跨配置变更保存；[ListDetailSceneStrategy] 在 Compact 窗口下逐页显示，
 * 在 Medium 及以上窗口把相邻的列表项与详情项合并为双栏。
 */
@Composable
fun APInspectorNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberListDetailSceneStrategy()),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeKey>(
                    metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoSpecSelectedPane() }),
                ) {
                    HomeScreen()
                }
            },
    )
}
