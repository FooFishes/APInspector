package work.foofish.apinspector.operation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import work.foofish.apinspector.R
import work.foofish.apinspector.openapi.OperationKey
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.OperationTitle
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors

/** 接口详情的三个 Tab（PRD 3），默认打开"文档"。 */
private enum class OperationTab(
    val title: Int,
) {
    DOC(R.string.operation_tab_doc),
    REQUEST(R.string.operation_tab_request),
    RESPONSE(R.string.operation_tab_response),
}

/**
 * 接口详情（PRD 3）：文档、请求与响应三个 Tab，各种窗口宽度下相同。
 *
 * @param showBack 单栏布局下显示返回按钮；双栏布局下位于详情栏，不需要返回。
 * @param requestTab 开始发送后调用 `onSend` 切到"响应"。
 * @param responseTab 恢复到当前参数后调用 `onRestore` 切到"请求"。
 */
@Composable
fun OperationScreen(
    operation: OperationKey,
    showBack: Boolean,
    onBack: () -> Unit,
    docTab: @Composable (Modifier) -> Unit,
    requestTab: @Composable (onSend: () -> Unit, Modifier) -> Unit,
    responseTab: @Composable (onRestore: () -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableStateOf(OperationTab.DOC) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.screen,
        topBar = {
            TopAppBar(
                title = { OperationTitle(operation) },
                navigationIcon = {
                    if (showBack) {
                        BackButton(onClick = onBack)
                    }
                },
                colors = screenTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(top = innerPadding.calculateTopPadding())) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.screen) {
                OperationTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(stringResource(entry.title), style = MaterialTheme.typography.titleSmallEmphasized) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                // 新 Tab 从它所在的一侧滑入一小段，方向与 Tab 的左右顺序一致。
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(spatial) { direction * it / SLIDE_FRACTION } + fadeIn(effects)) togetherWith fadeOut(effects)
                },
                label = "operation-tab",
            ) { current ->
                val content = Modifier.fillMaxSize()
                when (current) {
                    OperationTab.DOC -> docTab(content)
                    OperationTab.REQUEST -> requestTab({ tab = OperationTab.RESPONSE }, content)
                    OperationTab.RESPONSE -> responseTab({ tab = OperationTab.REQUEST }, content)
                }
            }
        }
    }
}

/** 切换 Tab 时内容滑入的距离：宽度的 1 / 8。 */
private const val SLIDE_FRACTION = 8
