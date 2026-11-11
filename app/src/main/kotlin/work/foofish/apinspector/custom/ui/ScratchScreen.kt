package work.foofish.apinspector.custom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import work.foofish.apinspector.R
import work.foofish.apinspector.custom.CustomRequestId
import work.foofish.apinspector.custom.CustomRequestsViewModel
import work.foofish.apinspector.ui.component.BackButton
import work.foofish.apinspector.ui.component.EmptyState
import work.foofish.apinspector.ui.component.LoadingState
import work.foofish.apinspector.ui.theme.screen
import work.foofish.apinspector.ui.theme.screenTopAppBarColors

/**
 * 临时请求列表（PRD 4.2）：不属于任何 API 文档的自定义请求，从首页固定置顶的入口进入。
 */
@Composable
fun ScratchScreen(
    viewModel: CustomRequestsViewModel,
    selected: CustomRequestId?,
    onOpen: (CustomRequestId) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = rememberCustomRequestsHost(viewModel, onOpen)
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scratch_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = stringResource(R.string.history_title))
                    }
                    AddCustomRequestMenu(host)
                },
                colors = screenTopAppBarColors(),
            )
        },
    ) { innerPadding ->
        val requests = state.requests
        when {
            requests == null -> {
                LoadingState(Modifier.padding(innerPadding))
            }

            requests.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        painterResource(R.drawable.ic_send),
                        stringResource(R.string.scratch_empty_title),
                        stringResource(R.string.scratch_empty_body),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = ListBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    customRequestItems(requests, selected, movable = true, host = host, onOpen = onOpen)
                }
            }
        }
    }
    CustomRequestDialogs(host, state.moveTargets)
}

/** 双栏布局下尚未选中临时请求时的详情栏占位。 */
@Composable
fun NoCustomRequestSelectedPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(painterResource(R.drawable.ic_send), stringResource(R.string.custom_none_selected), body = null)
    }
}

private val ListBottomPadding = 24.dp
