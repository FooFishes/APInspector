package work.foofish.apinspector.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CustomRequestPageState
import work.foofish.apinspector.custom.CustomRequestPageViewModel
import work.foofish.apinspector.custom.CustomRequestsOwner
import work.foofish.apinspector.custom.CustomRequestsViewModel
import work.foofish.apinspector.custom.ui.NoCustomRequestSelectedPane
import work.foofish.apinspector.custom.ui.ScratchScreen
import work.foofish.apinspector.operation.OperationScreen
import work.foofish.apinspector.request.RequestTarget
import work.foofish.apinspector.request.RequestViewModel
import work.foofish.apinspector.request.ui.RequestTab
import work.foofish.apinspector.response.ResponseViewModel
import work.foofish.apinspector.response.ui.ResponseTab
import work.foofish.apinspector.spec.SpecId
import work.foofish.apinspector.ui.component.OperationTitle

/** 临时请求列表与自定义请求页面（PRD 4.2）。 */
internal fun EntryProviderScope<NavKey>.customEntries(
    backStack: MutableList<NavKey>,
    multiPane: Boolean,
) {
    entry<ScratchKey>(metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NoCustomRequestSelectedPane() })) {
        ScratchScreen(
            viewModel =
                hiltViewModel<CustomRequestsViewModel, CustomRequestsViewModel.Factory>(creationCallback = {
                    it.create(CustomRequestsOwner(specId = null))
                }),
            selected = backStack.currentCustomRequest?.id,
            onOpen = { backStack.openCustomRequest(CustomRequestKey(it)) },
            onOpenHistory = { backStack.openHistory(HistoryKey(specId = null)) },
            onBack = { backStack.removeLastOrNull() },
        )
    }
    entry<CustomRequestKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
        CustomRequestPage(
            key = key,
            showBack = !multiPane,
            // 请求在列表中被删除后，它的页面随之关闭。
            onClose = { backStack.remove(key) },
            onBack = { backStack.removeLastOrNull() },
            onManageEnvironments = { backStack.openEnvironments(EnvironmentsKey(it)) },
            onOpenHistory = { backStack.openHistory(HistoryKey(it, customId = key.id)) },
        )
    }
}

/**
 * @param onManageEnvironments 管理所属 API 文档的环境；临时请求没有环境。
 * @param onOpenHistory 打开这个请求的记录；参数是它所属的 API 文档，临时请求为 `null`。
 */
@Composable
private fun CustomRequestPage(
    key: CustomRequestKey,
    showBack: Boolean,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onManageEnvironments: (SpecId) -> Unit,
    onOpenHistory: (SpecId?) -> Unit,
) {
    val page = hiltViewModel<CustomRequestPageViewModel, CustomRequestPageViewModel.Factory>(creationCallback = { it.create(key) })
    val state by page.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(state) {
        if (state == CustomRequestPageState.Missing) currentOnClose()
    }
    val loaded = state as? CustomRequestPageState.Loaded
    val target = RequestTarget.Custom(key.id)
    OperationScreen(
        title = {
            if (loaded != null) {
                OperationTitle(loaded.method, loaded.name)
            } else {
                Text(stringResource(R.string.custom_section_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        showBack = showBack,
        onBack = onBack,
        docTab = null,
        requestTab = { onSend, modifier ->
            RequestTab(
                viewModel = hiltViewModel<RequestViewModel, RequestViewModel.Factory>(creationCallback = { it.create(target) }),
                onSend = onSend,
                onManageEnvironments = { loaded?.specId?.let(onManageEnvironments) },
                modifier = modifier,
            )
        },
        responseTab = { onRestore, modifier ->
            ResponseTab(
                viewModel = hiltViewModel<ResponseViewModel, ResponseViewModel.Factory>(creationCallback = { it.create(target) }),
                onRestore = onRestore,
                onOpenHistory = { onOpenHistory(loaded?.specId) },
                modifier = modifier,
            )
        },
    )
}
